package com.ctey.cpmodule.Service;

import com.ctey.cpmodule.Context.CPContext;
import com.ctey.cpmodule.Module.DataSourceModule;
import com.ctey.cpmodule.Module.UUIDModule;
import com.ctey.cpmodule.Util.MessagePrintUtil;
import com.ctey.cpstatic.Entity.*;
import com.ctey.cpstatic.Enum.ConnectionStatus;
import com.ctey.cpstatic.Enum.RequestStatus;
import com.ctey.cpstatic.Static.CPUserStatic;
import com.ctey.cpstatic.Util.ModelInitUtil;
import com.mysql.cj.protocol.Message;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static com.ctey.cpmodule.Context.CPContext.*;
import static com.ctey.cpstatic.Static.CPCoreStatic.*;
import static com.ctey.cpstatic.Static.CPUserStatic.*;

@Component
public class RequestWorkService {
    private final ScheduledExecutorService cpExecutorExamineTask;
    private final ScheduledExecutorService cpExecutorRequestTask;
    private final CPHandlerService cpHandlerService;
    private final CPContext cpContext;
    private final UUIDModule uuidModule;
    private final DataSourceModule dataSourceModule;

    // 客户端获取数据库连接操作并发锁
    private static final ReentrantLock WORK_LOCK = new ReentrantLock();
    // 客户端所有存活线程请求的集合
    private final BlockingQueue<ScheduledFuture<?>> requestWorkTaskCollection = new LinkedBlockingQueue<>();
    // 定时清理线程请求集合的已被取消/已完成部分
    private ScheduledFuture<?> processClearFinishScheduledRequestTask;

    @Autowired
    public RequestWorkService(ScheduledExecutorService cpExecutorExamineTask, ScheduledExecutorService cpExecutorRequestTask, CPHandlerService cpHandlerService, CPContext cpContext, UUIDModule uuidModule, DataSourceModule dataSourceModule) {
        this.cpExecutorExamineTask = cpExecutorExamineTask;
        this.cpExecutorRequestTask = cpExecutorRequestTask;
        this.cpHandlerService = cpHandlerService;
        this.cpContext = cpContext;
        this.uuidModule = uuidModule;
        this.dataSourceModule = dataSourceModule;
    }

    @PostConstruct
    public void initRequestWorkSaveTask() {
        processClearFinishScheduledRequestTask = cpExecutorRequestTask.scheduleWithFixedDelay(this::processClearFinishScheduledRequest, CP_SAVE_TASK_OFFSET, CP_SAVE_TASK_DELAY2, TimeUnit.MILLISECONDS);
    }

    /*
     * requestWorkTaskStart()
     * 模拟客户端线程的并发请求,等待各线程指定的时间后尝试向连接池获取连接
     * @return
     * @Date: 2025/1/8 10:27
     */
    public CPHandleException requestWorkTaskStart(TaskStartReq req) {
        try {
            if (CP_HANDLE_LOCK.isLocked()) {
                return EXCEPTION_LOCKING;
            }
            if (!CP_IS_RUNNING.get()) {
                return EXCEPTION_HAS_STOP;
            }
            for (RequestWork work : req.getWorkList()) {
                requestWorkTaskCollection.put(cpExecutorRequestTask.schedule(() -> requestWorkArrive(work), work.getArrive(), TimeUnit.MILLISECONDS));
            }
            return null;
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
        return EXCEPTION_ERROR;
    }

    /*
     * requestWorkArrive()
     * 模拟客户端线程到达服务器,开始尝试向连接池获取连接执行任务
     * @return
     * @Date: 2025/1/7 20:40
     */
    public void requestWorkArrive(RequestWork work) {
        try {
            Instant signWaitTime = null;
            ConnectionEntity connectionEntity = null;
            String uuid = uuidModule.getUUIDStr();
            RequestEntity requestEntity = ModelInitUtil.InitRequest(uuid, work);
            cpContext.requestEntityHistoryMap.put(uuid, requestEntity);
            MessagePrintUtil.printRequestArrive(requestEntity);

            while(connectionEntity == null) {
                try {
                    // 双重检查锁获取连接,不同并发任务应当同时只有一个与连接池交互
                    WORK_LOCK.lock();
                    if (!cpContext.connectionIdleQueue.isEmpty()) {
                        connectionEntity = cpHandlerService.acquireConnection();
                    }
                } catch (Exception ex) { MessagePrintUtil.printException(ex); }
                finally { WORK_LOCK.unlock(); }
                // 调用内部获取连接的方法仍然有可能连接为空,此时进入基于信号量的阻塞态
                if (connectionEntity == null) {
                    if (signWaitTime == null) {
                        // 首次未获取到连接时,记录开始阻塞的时间点,向连接池发送新增空闲连接的请求
                        cpContext.requestLackConnectionQueue.put(CP_LACK_CONNECTION_SIGN);
                        MessagePrintUtil.printRequestWait(requestEntity);
                        signWaitTime = Instant.now();
                    }
                    requestEntity.setStatus(RequestStatus.STATUS_WAITING);
                    // 记录当前已阻塞的时间,与最大等待时间相减得到本次阻塞态的剩余时间
                    long leftWaitTime = MAX_WAIT_TIME - (long)(Duration.between(signWaitTime, Instant.now()).toMillis() * CP_REACQUIRE_FACTOR);
                    if (!IDLE_POOL_SEMAPHORE.tryAcquire(leftWaitTime, TimeUnit.MILLISECONDS)) { break; }
                }
            }

            // 如果到达最大等待时间仍未获取到连接,放弃连接请求并输出错误信息
            if (connectionEntity == null) {
                requestEntity.setStatus(RequestStatus.STATUS_ERROR);
                MessagePrintUtil.printRequestTimeout(requestEntity);
                return;
            }

            // 如果获取到空闲连接,设置工作进程的相关参数后执行任务
            Instant startWorkTime = Instant.now();
            connectionEntity.setStatus(ConnectionStatus.STATUS_WORKING);
            connectionEntity.setLastWork(startWorkTime.toEpochMilli());
            connectionEntity.setCount(connectionEntity.getCount() + 1);
            requestEntity.setStatus(RequestStatus.STATUS_WORKING);
            requestEntity.setAcquire(startWorkTime.toEpochMilli());
            connectionEntity.setRequest(requestEntity);
            MessagePrintUtil.printRequestAcquire(connectionEntity);
            requestWorkProcess(connectionEntity, work.getKeep());
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
    }

    /*
     * requestWorkProcess()
     * 模拟客户端线程到达服务器并已获取到连接,开始执行任务
     * @return
     * @Date: 2025/1/7 21:03
     */
    public void requestWorkProcess(ConnectionEntity connectionEntity, Long keep) {
        try {
            // 模拟连续持有连接的时间段,时延结束后将连接返回给连接池通知归还连接
            Thread.sleep(keep);
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
        finally { cpHandlerService.releaseConnection(connectionEntity); }
    }


    /*
     * processClearFinishScheduledRequest()
     * 定时处理已完成任务的客户端请求记录
     * @return
     * @Date: 2025/1/10 11:43
     */
    public void processClearFinishScheduledRequest() {
        try {
            if (!CP_IS_RUNNING.get()) { return; }
            List<ScheduledFuture<?>> readyToRemoveList = requestWorkTaskCollection.stream().filter(scheduledFuture -> {
                return scheduledFuture.isDone() || scheduledFuture.isCancelled();
            }).toList();
            requestWorkTaskCollection.removeAll(readyToRemoveList);
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
    }

    /*
     * handleStopCP()
     * 用户显式关闭连接池,返回关闭操作的执行结果
     * @return
     * @Date: 2025/1/10 09:57
     */
    public CPHandleException handleStopCP() {
        try {
            if (CP_HANDLE_LOCK.isLocked()) {
                return EXCEPTION_LOCKING;
            }
            if (!CP_IS_RUNNING.get()) {
                return EXCEPTION_HAS_STOP;
            }
            CP_HANDLE_LOCK.lock();
            Thread.sleep(CP_SAVE_TASK_DELAY2);
            while (!CP_IS_RUNNING.compareAndSet(true, false)) {}
            for (ScheduledFuture<?> scheduledFuture : requestWorkTaskCollection) {
                if (!scheduledFuture.isDone() && !scheduledFuture.isCancelled()) {
                    scheduledFuture.cancel(true);
                }
            }
            IDLE_POOL_SEMAPHORE.drainPermits();
            for (ConnectionEntity connectionEntity : cpContext.connectionEntityPoolMap.values()) {
                connectionEntity.setStatus(ConnectionStatus.STATUS_CLOSED);
                Optional.ofNullable(connectionEntity.getConnection()).ifPresent(connection -> {
                    try { connection.close(); }
                    catch (Exception ex) { MessagePrintUtil.printException(ex); }
                });
                Optional.ofNullable(connectionEntity.getRequest()).ifPresent(requestEntity -> {
                    requestEntity.setStatus(RequestStatus.STATUS_ERROR);
                });
            }
            return null;
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
        finally { CP_HANDLE_LOCK.unlock(); }
        return EXCEPTION_ERROR;
    }

    /*
     * handleRestartCP()
     * 用户显式重启连接池(默认处于打开状态,因此称重启),返回重启操作的执行结果
     * @return
     * @Date: 2025/1/10 09:57
     */
    public CPHandleException handleRestartCP() {
        try {
            if (CP_HANDLE_LOCK.isLocked()) {
                return EXCEPTION_LOCKING;
            }
            if (CP_IS_RUNNING.get()) {
                return EXCEPTION_HAS_START;
            }
            CP_HANDLE_LOCK.lock();
            CURRENT_IDLE_SIZE.set(INITIAL_POOL_SIZE);
            CURRENT_POOL_SIZE.set(INITIAL_POOL_SIZE);
            cpContext.connectionEntityPoolMap.clear();
            cpContext.connectionIdleQueue.clear();
            cpContext.requestLackConnectionQueue.clear();
            requestWorkTaskCollection.clear();
            for (int i = 0; i < MIN_POOL_SIZE; i++) {
                String uuid = uuidModule.getUUIDStr();
                Connection connection = dataSourceModule.getConnection();
                ConnectionEntity connectionEntity = ModelInitUtil.InitConnection(uuid, connection);
                cpContext.connectionEntityPoolMap.put(uuid, connectionEntity);
                cpContext.connectionEntityHistoryMap.put(uuid, connectionEntity);
                cpContext.connectionIdleQueue.add(connectionEntity);
            }
            while (!CP_IS_RUNNING.compareAndSet(false, true)) {}
            Thread.sleep(CP_SAVE_TASK_DELAY2);
            return null;
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
        finally { CP_HANDLE_LOCK.unlock(); }
        return EXCEPTION_ERROR;
    }

    /*
     * outPutCPExamine()
     * 输出连接池当前所有存活连接/历史任务及其状态列表
     * @return
     * @Date: 2025/1/8 18:00
     */
    public CPHandleException outPutCPExamine() {
        try {
            if (CP_HANDLE_LOCK.isLocked()) {
                return EXCEPTION_LOCKING;
            }
            if (!CP_IS_RUNNING.get()) {
                return EXCEPTION_HAS_STOP;
            }
            StringBuilder stb = new StringBuilder();
            List<ConnectionEntity> connectionEntityList = cpContext.connectionEntityPoolMap.values().stream().toList();
            for (int i = 1; i <= connectionEntityList.size(); i++) {
                ConnectionEntity connectionEntity = connectionEntityList.get(i-1);
                stb.append("CONNECTION ").append(i).append("/").append(connectionEntityList.size())
                        .append(" UUID:").append(connectionEntity.getUUID())
                        .append(" STATUS:").append(connectionEntity.getStatus());
                Logger.getLogger("ROOT").info(stb.toString());
                stb.setLength(0);
            }
            return null;
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
        return EXCEPTION_ERROR;
    }

}
