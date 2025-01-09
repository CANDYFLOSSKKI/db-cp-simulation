package com.ctey.cpmodule.Service;

import com.ctey.cpmodule.Context.CPContext;
import com.ctey.cpmodule.Module.DataSourceModule;
import com.ctey.cpmodule.Util.MessagePrintUtil;
import com.ctey.cpstatic.Entity.ConnectionEntity;
import com.ctey.cpstatic.Entity.RequestEntity;
import com.ctey.cpstatic.Enum.ConnectionStatus;
import com.ctey.cpstatic.Enum.RequestStatus;
import com.mysql.cj.protocol.Message;
import jakarta.annotation.PostConstruct;
import org.dromara.dynamictp.core.executor.ScheduledDtpExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.ctey.cpmodule.Context.CPContext.*;
import static com.ctey.cpstatic.Static.CPCoreStatic.*;
import static com.ctey.cpstatic.Static.CPUserStatic.*;

@Component
public class CPHandlerService {
    private final ScheduledExecutorService cpExecutorExamineTask;
    private final DataSourceModule dataSourceModule;
    private final CPContext cpContext;

    // 空闲连接队列操作并发锁
    public static final ReentrantLock IDLE_POOL_LOCK = new ReentrantLock();

    @Autowired
    public CPHandlerService(ScheduledExecutorService cpExecutorExamineTask, DataSourceModule dataSourceModule, CPContext cpContext) {
        this.cpExecutorExamineTask = cpExecutorExamineTask;
        this.dataSourceModule = dataSourceModule;
        this.cpContext = cpContext;
    }

    @PostConstruct
    public void initCPHandlerSaveTask() {
        cpExecutorExamineTask.scheduleWithFixedDelay(this::processLackConnection, 0L, CP_SAVE_TASK_DELAY, TimeUnit.MILLISECONDS);
        cpExecutorExamineTask.scheduleWithFixedDelay(this::processMaxWorkTimeConnection, 0L, CP_SAVE_TASK_DELAY2, TimeUnit.MILLISECONDS);
        cpExecutorExamineTask.scheduleWithFixedDelay(this::processMinIdleConnection, CP_SAVE_TASK_OFFSET2, CP_SAVE_TASK_DELAY2, TimeUnit.MILLISECONDS);
        cpExecutorExamineTask.scheduleWithFixedDelay(this::processMaxIdleConnection, CP_SAVE_TASK_OFFSET, CP_SAVE_TASK_DELAY2, TimeUnit.MILLISECONDS);
    }

    /*
     * acquireConnection()
     * 空闲连接队列请求获取空闲连接
     * @return
     * @Date: 2025/1/6 22:07
     */
    public ConnectionEntity acquireConnection() {
        try {
            IDLE_POOL_LOCK.lock();
            ConnectionEntity connectionEntity = cpContext.connectionIdleQueue.poll();
            // 获取空闲连接队列锁的操作和外部操作并非原子,仍然可能出现获取不到空闲连接的情况
            // 如果仍然获取不到空闲连接,返回NULL给外部客户端线程,通知其返回队列继续等待
            if (connectionEntity != null) {
                MessagePrintUtil.printAcquireConnection(CURRENT_IDLE_SIZE.decrementAndGet(), connectionEntity);
            }
            return connectionEntity;
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
        finally { IDLE_POOL_LOCK.unlock(); }
        return null;
    }

    /*
     * releaseConnection()
     * 客户端线程执行完任务后通知连接池回收连接
     * @return
     * @Date: 2025/1/7 19:59
     */
    public void releaseConnection(ConnectionEntity connectionEntity) {
        try {
            Instant releaseTime = Instant.now();
            // 设置本次连接工作结束的相关参数
            connectionEntity.setLastRelease(releaseTime.toEpochMilli());
            RequestEntity requestEntity = connectionEntity.getRequest();
            requestEntity.setStatus(RequestStatus.STATUS_RELEASED);
            requestEntity.setRelease(releaseTime.toEpochMilli());
            MessagePrintUtil.printRequestRelease(connectionEntity);

            // 如果连接使用次数超出限制,关闭该连接(连接使用次数在连接被获取时自增)
            // 如果执行连接健康度检查(SELECT 1测试查询)失败,同样会关闭该连接
            if (connectionEntity.getCount() >= MAX_USE_COUNT || !checkConnectionHealth(connectionEntity)) {
                setConnectionClose(connectionEntity);
            // 如果连接使用次数未超限制,将连接重新添加进空闲连接队列,释放信号量通知阻塞的客户端线程(如果有)获取
            } else {
                connectionEntity.setRequest(null);
                connectionEntity.setStatus(ConnectionStatus.STATUS_IDLE);
                try {
                    IDLE_POOL_LOCK.lock();
                    cpContext.connectionIdleQueue.put(connectionEntity);
                    MessagePrintUtil.printRestoreConnection(CURRENT_IDLE_SIZE.incrementAndGet(), connectionEntity);
                } catch (Exception ex) { MessagePrintUtil.printException(ex); }
                finally { IDLE_POOL_LOCK.unlock(); }
                if (!cpContext.requestLackConnectionQueue.isEmpty()) {
                    IDLE_POOL_SEMAPHORE.release();
                }
            }
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
    }

    /*
     * processLackConnection()
     * 客户端没有空闲连接,提示连接池创建连接时,通过异步任务创建新空闲连接并释放信号量
     * @return
     * @Date: 2025/1/7 21:47
     */
    public void processLackConnection() {
        try {
            while (!cpContext.requestLackConnectionQueue.isEmpty()) {
                if (cpContext.requestLackConnectionQueue.poll() == null) { break; }
                MessagePrintUtil.printSaveTaskStart("PROCESS LACK CONNECTION");
                // 当前连接数未超过最大连接数时才允许新建连接
                if (CURRENT_POOL_SIZE.get() < MAX_POOL_SIZE) {
                    ConnectionEntity connectionEntity = cpContext.createConnection();
                    cpContext.connectionEntityHistoryMap.put(connectionEntity.getUUID(), connectionEntity);
                    CURRENT_POOL_SIZE.incrementAndGet();
                    // 将新建的空闲连接添加进空闲连接队列,释放信号量通知阻塞的客户端线程获取
                    try {
                        IDLE_POOL_LOCK.lock();
                        cpContext.connectionEntityPoolMap.put(connectionEntity.getUUID(), connectionEntity);
                        cpContext.connectionIdleQueue.put(connectionEntity);
                        MessagePrintUtil.printNewConnection(CURRENT_IDLE_SIZE.incrementAndGet(), connectionEntity);
                    } catch (Exception ex) { MessagePrintUtil.printException(ex); }
                    finally { IDLE_POOL_LOCK.unlock(); }
                    IDLE_POOL_SEMAPHORE.release();
                }
            }
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
    }

    /*
     * processMaxWorkTimeConnection()
     * 检查客户端线程是否持有连接时间过长,关闭连续工作时间过长的连接
     * @return
     * @Date: 2025/1/8 15:05
     */
    public void processMaxWorkTimeConnection() {
        try {
            MessagePrintUtil.printSaveTaskStart("PROCESS MAX WORK TIME CONNECTION");
            Instant examineTime = Instant.now();
            List<String> maxWorkTimeUUIDList = new ArrayList<>();
            // 记录开始工作时间和当前时间戳时间段过长的所有连接ID
            cpContext.connectionEntityPoolMap.values().stream().filter(connectionEntity -> {
                if (connectionEntity.getStatus() != ConnectionStatus.STATUS_WORKING) { return false; }
                Duration workTime = Duration.between(Instant.ofEpochMilli(connectionEntity.getLastWork()), examineTime);
                return workTime.compareTo(Duration.ofMillis(MAX_CONNECT_TIME)) >= 0;
            }).forEach(connectionEntity -> {
                maxWorkTimeUUIDList.add(connectionEntity.getUUID());
                try {
                    // 关闭所有筛选出的目标连接,相应的客户端线程置为错误状态
                    connectionEntity.getConnection().close();
                    MessagePrintUtil.printCloseConnection(connectionEntity);
                    MessagePrintUtil.printRequestDisrupt(connectionEntity);
                    connectionEntity.setStatus(ConnectionStatus.STATUS_CLOSED);
                    connectionEntity.setLastRelease(examineTime.toEpochMilli());
                    RequestEntity requestEntity = connectionEntity.getRequest();
                    requestEntity.setStatus(RequestStatus.STATUS_ERROR);
                    requestEntity.setRelease(examineTime.toEpochMilli());
                } catch (Exception ex) { MessagePrintUtil.printException(ex); }
            });
            maxWorkTimeUUIDList.forEach(cpContext.connectionEntityPoolMap::remove);
            CURRENT_POOL_SIZE.addAndGet(-maxWorkTimeUUIDList.size());
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
    }

    /*
     * processMinIdleConnection()
     * 保证空闲连接队列中的空闲连接个数不小于最小值(直到达到最大连接数)
     * @return
     * @Date: 2025/1/8 14:41
     */
    public void processMinIdleConnection() {
        try {
            MessagePrintUtil.printSaveTaskStart("PROCESS MIN IDLE CONNECTION");
            int idleSize = CURRENT_IDLE_SIZE.get();
            int poolSize = CURRENT_POOL_SIZE.get();
            if (idleSize >= MIN_IDLE_SIZE || poolSize >= MAX_POOL_SIZE) { return; }
            // 未到达最大连接数时,按缺少的空闲连接数补齐空闲连接
            // 已到达最大连接数时,最多补齐空闲连接到总连接数=最大连接数
            int expectToAddValue = Math.min(MIN_IDLE_SIZE - idleSize, MAX_POOL_SIZE - poolSize);
            List<ConnectionEntity> connectionEntityList = new ArrayList<>();
            for (int i = 0; i < expectToAddValue; i++) {
                ConnectionEntity connectionEntity = cpContext.createConnection();
                cpContext.connectionEntityHistoryMap.put(connectionEntity.getUUID(), connectionEntity);
                connectionEntityList.add(connectionEntity);
            }
            CURRENT_POOL_SIZE.addAndGet(expectToAddValue);
            // 先创建目标个数的连接,再对空闲连接队列上锁,因此整体并非原子操作,可能导致添加后的连接数>最大连接数
            // 由于有空闲时间过长的回收机制,该情况可用于应对客户端线程流量高峰的处理,并发量不足时会自动回收
            try {
                IDLE_POOL_LOCK.lock();
                for (ConnectionEntity connectionEntity : connectionEntityList) {
                    cpContext.connectionEntityPoolMap.put(connectionEntity.getUUID(), connectionEntity);
                    cpContext.connectionIdleQueue.put(connectionEntity);
                    MessagePrintUtil.printNewConnection(CURRENT_IDLE_SIZE.incrementAndGet(), connectionEntity);
                }
            } catch (Exception ex) { MessagePrintUtil.printException(ex); }
            finally { IDLE_POOL_LOCK.unlock(); }
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
    }

    /*
     * processMaxIdleConnection()
     * 定时处理空闲时间过长的连接,删除最小空闲连接数之外的连接,重置最小空闲连接数之内的连接
     * @return
     * @Date: 2025/1/7 22:03
     */
    public void processMaxIdleConnection() {
        try {
            MessagePrintUtil.printSaveTaskStart("PROCESS MAX IDLE CONNECTION");
            // 获取当前连接池中的总空闲连接数
            // 如果总空闲连接数超过了最小空闲连接数,检测出的空闲连接中超出的部分将被直接删除
            // 如果总空闲连接数未超过最小空闲连接数,检测出的所有空闲连接全部重新创建并添加进空闲连接队列
            IDLE_POOL_LOCK.lock();
            int expectPoolSize = CURRENT_IDLE_SIZE.get();
            int expectToRemoveValue = MIN_IDLE_SIZE >= expectPoolSize ? 0 : expectPoolSize - MIN_IDLE_SIZE;
            Instant examineTime = Instant.now();
            List<ConnectionEntity> collectConnectionEntityList = cpContext.connectionIdleQueue.stream()
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
            if (collectConnectionEntityList.isEmpty()) { return; }
            cpContext.connectionIdleQueue.removeAll(collectConnectionEntityList);
            int expectToProcessValue = collectConnectionEntityList.size();

            // 如果空闲连接数在最小空闲连接数内,不会删除任何空闲连接,对所有空闲连接的状态进行重置
            // 如果空闲连接数超过最小空闲连接数,筛选出其中空闲时间最长的一部分连接关闭,剩余的空闲连接状态重置
            // 上述逻辑可得出"如果有足够数量的空闲时间过长的连接时,应该关闭多少个连接",实际还需要考虑空闲时间达标的连接具体有多少个
            if (expectToRemoveValue == 0) {
                collectConnectionEntityList.forEach(this::setConnectionRestart);
            } else if (expectToRemoveValue < expectToProcessValue) {
                CURRENT_IDLE_SIZE.addAndGet(-expectToRemoveValue);
                collectConnectionEntityList.subList(0, expectToRemoveValue).forEach(this::setConnectionClose);
                collectConnectionEntityList.subList(expectToRemoveValue, expectToProcessValue).forEach(this::setConnectionRestart);
            } else {
                CURRENT_IDLE_SIZE.addAndGet(-expectToProcessValue);
                collectConnectionEntityList.forEach(this::setConnectionClose);
            }
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
        finally { IDLE_POOL_LOCK.unlock(); }
    }

    /*
     * setConnectionClose()
     * 将已开启的连接设置为关闭状态
     * @return
     * @Date: 2025/1/8 14:36
     */
    public void setConnectionClose(ConnectionEntity connectionEntity) {
        try {
            connectionEntity.setStatus(ConnectionStatus.STATUS_CLOSED);
            cpContext.connectionEntityPoolMap.remove(connectionEntity.getUUID());
            CURRENT_POOL_SIZE.decrementAndGet();
            connectionEntity.getConnection().close();
            connectionEntity.setConnection(null);
            MessagePrintUtil.printCloseConnection(connectionEntity);
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
    }

    /*
     * setConnectionRestart()
     * 重新初始化已开启的连接实例中的连接对象,并重置所有参数
     * @return
     * @Date: 2025/1/8 14:36
     */
    public void setConnectionRestart(ConnectionEntity connectionEntity) {
        try {
            connectionEntity.setStatus(ConnectionStatus.STATUS_RESTARTING);
            connectionEntity.getConnection().close();
            connectionEntity.setConnection(dataSourceModule.getConnection());
            connectionEntity.setStart(System.currentTimeMillis());
            connectionEntity.setCount(0);
            connectionEntity.setLastWork(null);
            connectionEntity.setLastRelease(null);
            connectionEntity.setStatus(ConnectionStatus.STATUS_IDLE);
            cpContext.connectionIdleQueue.put(connectionEntity);
            MessagePrintUtil.printRestartConnection(connectionEntity);
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
    }

    /*
     * checkConnectionHealth()
     * 回收从客户端线程归还的连接时,通过测试查询判断连接的健康度
     * @return
     * @Date: 2025/1/8 20:59
     */
    public boolean checkConnectionHealth(ConnectionEntity connectionEntity) {
        try (Statement statement = connectionEntity.getConnection().createStatement()) {
            return statement.executeQuery("SELECT 1").next();
        } catch (Exception ex) { MessagePrintUtil.printException(ex); }
        return false;
    }

    /*
     * outPutCPExamine()
     * 输出连接池当前所有存活连接/历史任务及其状态列表
     * @return
     * @Date: 2025/1/8 18:00
     */
    public void outPutCPExamine() {
        Logger LOGGER = Logger.getLogger("ROOT");
        StringBuilder STB = new StringBuilder();
        List<ConnectionEntity> connectionEntityList = cpContext.connectionEntityPoolMap.values().stream().toList();
        // LOGGER.info("CURRENT_POOL_SIZE:" + CURRENT_POOL_SIZE.get());
        // LOGGER.info("CURRENT_IDLE_SIZE:" + CURRENT_IDLE_SIZE.get());
        for (int i = 1; i <= connectionEntityList.size(); i++) {
            ConnectionEntity connectionEntity = connectionEntityList.get(i-1);
            STB.append("CONNECTION ").append(i).append("/").append(connectionEntityList.size())
                    .append(" UUID:").append(connectionEntity.getUUID())
                    .append(" STATUS:").append(connectionEntity.getStatus());
            LOGGER.info(STB.toString());
            STB.setLength(0);
        }
        List<ConnectionEntity> idleConnectionEntityList = cpContext.connectionIdleQueue.stream().toList();
        for (int i = 1; i <= idleConnectionEntityList.size(); i++) {
            ConnectionEntity connectionEntity = idleConnectionEntityList.get(i-1);
            STB.append("IDLE CONNECTION ").append(i).append("/").append(idleConnectionEntityList.size())
                    .append(" UUID:").append(connectionEntity.getUUID())
                    .append(" STATUS:").append(connectionEntity.getStatus());
            LOGGER.info(STB.toString());
            STB.setLength(0);
        }
//         List<RequestEntity> requestEntityList = cpContext.requestEntityHistoryMap.values().stream().toList();
//         for (int i = 1; i <= requestEntityList.size(); i++) {
//             RequestEntity requestEntity = requestEntityList.get(i-1);
//             STB.append("REQUEST ").append(i).append("/").append(requestEntityList.size())
//                     .append(" UUID:").append(requestEntity.getUUID())
//                     .append(" STATUS:").append(requestEntity.getStatus());
//             LOGGER.info(STB.toString());
//             STB.setLength(0);
//         }
    }

}
