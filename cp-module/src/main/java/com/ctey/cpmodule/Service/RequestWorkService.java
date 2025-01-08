package com.ctey.cpmodule.Service;

import com.ctey.cpmodule.Context.CPContext;
import com.ctey.cpmodule.Module.UUIDModule;
import com.ctey.cpmodule.Util.MessagePrintUtil;
import com.ctey.cpstatic.Entity.ConnectionEntity;
import com.ctey.cpstatic.Entity.RequestEntity;
import com.ctey.cpstatic.Entity.RequestWork;
import com.ctey.cpstatic.Entity.TaskStartReq;
import com.ctey.cpstatic.Enum.ConnectionStatus;
import com.ctey.cpstatic.Enum.RequestStatus;
import com.ctey.cpstatic.Util.ModelInitUtil;
import com.mysql.cj.protocol.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.ctey.cpmodule.Context.CPContext.IDLE_POOL_SEMAPHORE;
import static com.ctey.cpstatic.Static.CPCoreStatic.MAX_WAIT_TIME;
import static com.ctey.cpstatic.Static.CPUserStatic.CP_LACK_CONNECTION_SIGN;

@Component
public class RequestWorkService {
    private final ScheduledExecutorService cpExecutorRequestTask;
    private final CPHandlerService cpHandlerService;
    private final CPContext cpContext;
    private final UUIDModule uuidModule;

    // 客户端获取数据库连接操作并发锁
    private static final ReentrantLock WORK_LOCK = new ReentrantLock();

    @Autowired
    public RequestWorkService(ScheduledExecutorService cpExecutorRequestTask, CPHandlerService cpHandlerService, CPContext cpContext, UUIDModule uuidModule) {
        this.cpExecutorRequestTask = cpExecutorRequestTask;
        this.cpHandlerService = cpHandlerService;
        this.cpContext = cpContext;
        this.uuidModule = uuidModule;
    }

    /*
     * requestWorkTaskStart()
     * 模拟客户端线程的并发请求,等待各线程指定的时间后尝试向连接池获取连接
     * @return
     * @Date: 2025/1/8 10:27
     */
    public void requestWorkTaskStart(TaskStartReq req) {
        try {
            for (RequestWork work : req.getWorkList()) {
                cpExecutorRequestTask.schedule(() -> requestWorkArrive(work), work.getArrive(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
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
                    long leftWaitTime = MAX_WAIT_TIME - Duration.between(signWaitTime, Instant.now()).toMillis();
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

}
