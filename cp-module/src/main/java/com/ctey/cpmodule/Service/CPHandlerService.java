package com.ctey.cpmodule.Service;

import com.ctey.cpmodule.Context.CPContext;
import com.ctey.cpmodule.Context.DataSourceContext;
import com.ctey.cpmodule.Util.MsgPrintUtil;
import com.ctey.cpstatic.Entity.ConnEntity;
import com.ctey.cpstatic.Entity.ReqEntity;
import com.ctey.cpstatic.Enum.ConnStatus;
import com.ctey.cpstatic.Enum.ReqStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.ctey.cpmodule.Context.CPContext.*;
import static com.ctey.cpstatic.Static.CPCoreStatic.*;
import static com.ctey.cpstatic.Static.CPUserStatic.*;

@Component
public class CPHandlerService {
    private final ScheduledExecutorService cpExecutorExamineTask;
    private final DataSourceContext dataSourceContext;
    private final CPContext cpContext;

    // 空闲连接队列操作并发锁
    public static final ReentrantLock IDLE_POOL_LOCK = new ReentrantLock();

    // 连接池守护线程定时任务调度器
    public ScheduledFuture<?> processLackConnectionTask;
    public ScheduledFuture<?> processMaxWorkTimeConnectionTask;
    public ScheduledFuture<?> processMinIdleConnectionTask;
    public ScheduledFuture<?> processMaxIdleConnectionTask;


    @Autowired
    public CPHandlerService(ScheduledExecutorService cpExecutorExamineTask, DataSourceContext dataSourceContext, CPContext cpContext) {
        this.cpExecutorExamineTask = cpExecutorExamineTask;
        this.dataSourceContext = dataSourceContext;
        this.cpContext = cpContext;
    }

    @PostConstruct
    public void initCPHandlerSaveTask() {
        processLackConnectionTask = cpExecutorExamineTask.scheduleWithFixedDelay(this::processLackConnection, 0L, CP_SAVE_TASK_DELAY, TimeUnit.MILLISECONDS);
        processMaxWorkTimeConnectionTask = cpExecutorExamineTask.scheduleWithFixedDelay(this::processMaxWorkTimeConnection, 0L, CP_SAVE_TASK_DELAY2, TimeUnit.MILLISECONDS);
        processMinIdleConnectionTask = cpExecutorExamineTask.scheduleWithFixedDelay(this::processMinIdleConnection, CP_SAVE_TASK_OFFSET2, CP_SAVE_TASK_DELAY2, TimeUnit.MILLISECONDS);
        processMaxIdleConnectionTask = cpExecutorExamineTask.scheduleWithFixedDelay(this::processMaxIdleConnection, CP_SAVE_TASK_OFFSET, CP_SAVE_TASK_DELAY2, TimeUnit.MILLISECONDS);
    }

    /*
     * acquireConnection()
     * 空闲连接队列请求获取空闲连接
     * @return
     * @Date: 2025/1/6 22:07
     */
    public ConnEntity acquireConnection() {
        try {
            IDLE_POOL_LOCK.lock();
            ConnEntity connEntity = cpContext.connectionIdleQueue.poll();
            // 获取空闲连接队列锁的操作和外部操作并非原子,仍然可能出现获取不到空闲连接的情况
            // 如果仍然获取不到空闲连接,返回NULL给外部客户端线程,通知其返回队列继续等待
            if (connEntity != null) {
                MsgPrintUtil.printAcquireConnection(CURRENT_IDLE_SIZE.decrementAndGet(), connEntity);
            }
            return connEntity;
        } catch (Exception ex) { MsgPrintUtil.printException(ex); }
        finally { IDLE_POOL_LOCK.unlock(); }
        return null;
    }

    /*
     * releaseConnection()
     * 客户端线程执行完任务后通知连接池回收连接
     * @return
     * @Date: 2025/1/7 19:59
     */
    public void releaseConnection(ConnEntity connEntity) {
        try {
            Instant releaseTime = Instant.now();
            // 设置本次连接工作结束的相关参数
            connEntity.setLastRelease(releaseTime.toEpochMilli());
            ReqEntity reqEntity = connEntity.getRequest();
            reqEntity.setStatus(ReqStatus.STATUS_RELEASED);
            reqEntity.setRelease(releaseTime.toEpochMilli());
            MsgPrintUtil.printRequestRelease(connEntity);

            // 如果连接使用次数超出限制,关闭该连接(连接使用次数在连接被获取时自增)
            // 如果执行连接健康度检查(SELECT 1测试查询)失败,同样会关闭该连接
            if (connEntity.getCount() >= MAX_USE_COUNT || !checkConnectionHealth(connEntity)) {
                setConnectionClose(connEntity);
            // 如果连接使用次数未超限制,将连接重新添加进空闲连接队列,释放信号量通知阻塞的客户端线程(如果有)获取
            } else {
                connEntity.setRequest(null);
                connEntity.setStatus(ConnStatus.STATUS_IDLE);
                try {
                    IDLE_POOL_LOCK.lock();
                    cpContext.connectionIdleQueue.put(connEntity);
                    MsgPrintUtil.printRestoreConnection(CURRENT_IDLE_SIZE.incrementAndGet(), connEntity);
                } catch (Exception ex) { MsgPrintUtil.printException(ex); }
                finally { IDLE_POOL_LOCK.unlock(); }
                if (!cpContext.requestLackConnectionQueue.isEmpty()) {
                    IDLE_POOL_SEMAPHORE.release();
                }
            }
        } catch (Exception ex) { MsgPrintUtil.printException(ex); }
    }

    /*
     * processLackConnection()
     * 客户端没有空闲连接,提示连接池创建连接时,通过异步任务创建新空闲连接并释放信号量
     * @return
     * @Date: 2025/1/7 21:47
     */
    public void processLackConnection() {
        try {
            if (!CP_IS_RUNNING.get()) { return; }
            while (!cpContext.requestLackConnectionQueue.isEmpty()) {
                if (cpContext.requestLackConnectionQueue.poll() == null) { break; }
                MsgPrintUtil.printSaveTaskStart("PROCESS LACK CONNECTION");
                // 当前连接数未超过最大连接数时才允许新建连接
                if (CURRENT_POOL_SIZE.get() < MAX_POOL_SIZE) {
                    ConnEntity connEntity = cpContext.createConnection();
                    cpContext.connectionEntityHistoryMap.put(connEntity.getUUID(), connEntity);
                    CURRENT_POOL_SIZE.incrementAndGet();
                    // 将新建的空闲连接添加进空闲连接队列,释放信号量通知阻塞的客户端线程获取
                    try {
                        IDLE_POOL_LOCK.lock();
                        cpContext.connectionEntityPoolMap.put(connEntity.getUUID(), connEntity);
                        cpContext.connectionIdleQueue.put(connEntity);
                        MsgPrintUtil.printNewConnection(CURRENT_IDLE_SIZE.incrementAndGet(), connEntity);
                    } catch (Exception ex) { MsgPrintUtil.printException(ex); }
                    finally { IDLE_POOL_LOCK.unlock(); }
                    IDLE_POOL_SEMAPHORE.release();
                }
            }
        } catch (Exception ex) { MsgPrintUtil.printException(ex); }
    }

    /*
     * processMaxWorkTimeConnection()
     * 检查客户端线程是否持有连接时间过长,关闭连续工作时间过长的连接
     * @return
     * @Date: 2025/1/8 15:05
     */
    public void processMaxWorkTimeConnection() {
        try {
            if (!CP_IS_RUNNING.get()) { return; }
            MsgPrintUtil.printSaveTaskStart("PROCESS MAX WORK TIME CONNECTION");
            Instant examineTime = Instant.now();
            List<String> maxWorkTimeUUIDList = new ArrayList<>();
            // 记录开始工作时间和当前时间戳时间段过长的所有连接ID
            cpContext.connectionEntityPoolMap.values().stream().filter(connectionEntity -> {
                if (connectionEntity.getStatus() != ConnStatus.STATUS_WORKING) { return false; }
                Duration workTime = Duration.between(Instant.ofEpochMilli(connectionEntity.getLastWork()), examineTime);
                return workTime.compareTo(Duration.ofMillis(MAX_CONNECT_TIME)) >= 0;
            }).forEach(connectionEntity -> {
                maxWorkTimeUUIDList.add(connectionEntity.getUUID());
                try {
                    // 关闭所有筛选出的目标连接,相应的客户端线程置为错误状态
                    connectionEntity.getConnection().close();
                    MsgPrintUtil.printCloseConnection(connectionEntity);
                    MsgPrintUtil.printRequestDisrupt(connectionEntity);
                    connectionEntity.setStatus(ConnStatus.STATUS_CLOSED);
                    connectionEntity.setLastRelease(examineTime.toEpochMilli());
                    ReqEntity reqEntity = connectionEntity.getRequest();
                    reqEntity.setStatus(ReqStatus.STATUS_ERROR);
                    reqEntity.setRelease(examineTime.toEpochMilli());
                } catch (Exception ex) { MsgPrintUtil.printException(ex); }
            });
            maxWorkTimeUUIDList.forEach(cpContext.connectionEntityPoolMap::remove);
            CURRENT_POOL_SIZE.addAndGet(-maxWorkTimeUUIDList.size());
        } catch (Exception ex) { MsgPrintUtil.printException(ex); }
    }

    /*
     * processMinIdleConnection()
     * 保证空闲连接队列中的空闲连接个数不小于最小值(直到达到最大连接数)
     * @return
     * @Date: 2025/1/8 14:41
     */
    public void processMinIdleConnection() {
        try {
            if (!CP_IS_RUNNING.get()) { return; }
            MsgPrintUtil.printSaveTaskStart("PROCESS MIN IDLE CONNECTION");
            int idleSize = CURRENT_IDLE_SIZE.get();
            int poolSize = CURRENT_POOL_SIZE.get();
            if (idleSize >= MIN_IDLE_SIZE || poolSize >= MAX_POOL_SIZE) { return; }
            // 未到达最大连接数时,按缺少的空闲连接数补齐空闲连接
            // 已到达最大连接数时,最多补齐空闲连接到总连接数=最大连接数
            int expectToAddValue = Math.min(MIN_IDLE_SIZE - idleSize, MAX_POOL_SIZE - poolSize);
            List<ConnEntity> connEntityList = new ArrayList<>();
            for (int i = 0; i < expectToAddValue; i++) {
                ConnEntity connEntity = cpContext.createConnection();
                cpContext.connectionEntityHistoryMap.put(connEntity.getUUID(), connEntity);
                connEntityList.add(connEntity);
            }
            CURRENT_POOL_SIZE.addAndGet(expectToAddValue);
            // 先创建目标个数的连接,再对空闲连接队列上锁,因此整体并非原子操作,可能导致添加后的连接数>最大连接数
            // 由于有空闲时间过长的回收机制,该情况可用于应对客户端线程流量高峰的处理,并发量不足时会自动回收
            try {
                IDLE_POOL_LOCK.lock();
                for (ConnEntity connEntity : connEntityList) {
                    cpContext.connectionEntityPoolMap.put(connEntity.getUUID(), connEntity);
                    cpContext.connectionIdleQueue.put(connEntity);
                    MsgPrintUtil.printNewConnection(CURRENT_IDLE_SIZE.incrementAndGet(), connEntity);
                }
            } catch (Exception ex) { MsgPrintUtil.printException(ex); }
            finally { IDLE_POOL_LOCK.unlock(); }
        } catch (Exception ex) { MsgPrintUtil.printException(ex); }
    }

    /*
     * processMaxIdleConnection()
     * 定时处理空闲时间过长的连接,删除最小空闲连接数之外的连接,重置最小空闲连接数之内的连接
     * @return
     * @Date: 2025/1/7 22:03
     */
    public void processMaxIdleConnection() {
        try {
            if (!CP_IS_RUNNING.get()) { return; }
            MsgPrintUtil.printSaveTaskStart("PROCESS MAX IDLE CONNECTION");
            // 获取当前连接池中的总空闲连接数
            // 如果总空闲连接数超过了最小空闲连接数,检测出的空闲连接中超出的部分将被直接删除
            // 如果总空闲连接数未超过最小空闲连接数,检测出的所有空闲连接全部重新创建并添加进空闲连接队列
            try {
                IDLE_POOL_LOCK.lock();
                int expectPoolSize = CURRENT_IDLE_SIZE.get();
                int expectToRemoveValue = MIN_IDLE_SIZE >= expectPoolSize ? 0 : expectPoolSize - MIN_IDLE_SIZE;
                Instant examineTime = Instant.now();
                List<ConnEntity> collectConnEntityList = cpContext.connectionIdleQueue.stream()
                        .map(connectionEntity -> {
                            Instant releaseTime = connectionEntity.getLastRelease() != null
                                    ? Instant.ofEpochMilli(connectionEntity.getLastRelease())
                                    : Instant.ofEpochMilli(connectionEntity.getStart());
                            return Map.entry(releaseTime, connectionEntity);
                        })
                        .filter(entry -> Duration.between(entry.getKey(), examineTime).toMillis() > MAX_IDLE_TIME)
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .toList();
                // 如果没有连接处于空闲时间过长的状态,即使此时空闲连接数超过了最小空闲连接数也不会对其关闭
                if (collectConnEntityList.isEmpty()) { return; }
                cpContext.connectionIdleQueue.removeAll(collectConnEntityList);
                int expectToProcessValue = collectConnEntityList.size();

                // 如果空闲连接数在最小空闲连接数内,不会删除任何空闲连接,对所有空闲连接的状态进行重置
                // 如果空闲连接数超过最小空闲连接数,筛选出其中空闲时间最长的一部分连接关闭,剩余的空闲连接状态重置
                // 上述逻辑可得出"如果有足够数量的空闲时间过长的连接时,应该关闭多少个连接",实际还需要考虑空闲时间达标的连接具体有多少个
                if (expectToRemoveValue == 0) {
                    collectConnEntityList.forEach(this::setConnectionRestart);
                } else if (expectToRemoveValue < expectToProcessValue) {
                    CURRENT_IDLE_SIZE.addAndGet(-expectToRemoveValue);
                    collectConnEntityList.subList(0, expectToRemoveValue).forEach(this::setConnectionClose);
                    collectConnEntityList.subList(expectToRemoveValue, expectToProcessValue).forEach(this::setConnectionRestart);
                } else {
                    CURRENT_IDLE_SIZE.addAndGet(-expectToProcessValue);
                    collectConnEntityList.forEach(this::setConnectionClose);
                }
            } catch (Exception ex) { MsgPrintUtil.printException(ex); }
            finally { IDLE_POOL_LOCK.unlock(); }
        } catch (Exception ex) { MsgPrintUtil.printException(ex); }
    }

    /*
     * setConnectionClose()
     * 将已开启的连接设置为关闭状态
     * @return
     * @Date: 2025/1/8 14:36
     */
    public void setConnectionClose(ConnEntity connEntity) {
        try {
            connEntity.setStatus(ConnStatus.STATUS_CLOSED);
            cpContext.connectionEntityPoolMap.remove(connEntity.getUUID());
            CURRENT_POOL_SIZE.decrementAndGet();
            connEntity.getConnection().close();
            connEntity.setConnection(null);
            MsgPrintUtil.printCloseConnection(connEntity);
        } catch (Exception ex) { MsgPrintUtil.printException(ex); }
    }

    /*
     * setConnectionRestart()
     * 重新初始化已开启的连接实例中的连接对象,并重置所有参数
     * @return
     * @Date: 2025/1/8 14:36
     */
    public void setConnectionRestart(ConnEntity connEntity) {
        try {
            connEntity.setStatus(ConnStatus.STATUS_RESTARTING);
            connEntity.getConnection().close();
            connEntity.setConnection(dataSourceContext.getConnection());
            connEntity.setStart(System.currentTimeMillis());
            connEntity.setCount(0);
            connEntity.setLastWork(null);
            connEntity.setLastRelease(null);
            connEntity.setStatus(ConnStatus.STATUS_IDLE);
            cpContext.connectionIdleQueue.put(connEntity);
            MsgPrintUtil.printRestartConnection(connEntity);
        } catch (Exception ex) { MsgPrintUtil.printException(ex); }
    }

    /*
     * checkConnectionHealth()
     * 回收从客户端线程归还的连接时,通过测试查询判断连接的健康度
     * @return
     * @Date: 2025/1/8 20:59
     */
    public boolean checkConnectionHealth(ConnEntity connEntity) {
        try (Statement statement = connEntity.getConnection().createStatement()) {
            return statement.executeQuery("SELECT 1").next();
        } catch (Exception ex) { MsgPrintUtil.printException(ex); }
        return false;
    }

}
