package com.ctey.cpmodule.Context;

import com.ctey.cpmodule.Module.DataSourceModule;
import com.ctey.cpmodule.Module.UUIDModule;
import com.ctey.cpmodule.Util.MessagePrintUtil;
import com.ctey.cpstatic.Entity.ConnectionEntity;
import com.ctey.cpstatic.Entity.RequestEntity;
import com.ctey.cpstatic.Entity.RequestWork;
import com.ctey.cpstatic.Enum.ConnectionStatus;
import com.ctey.cpstatic.Enum.RequestStatus;
import com.ctey.cpstatic.Util.ModelInitUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.dromara.dynamictp.core.executor.OrderedDtpExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static com.ctey.cpstatic.Enum.ConnectionStatus.STATUS_IDLE;
import static com.ctey.cpstatic.Enum.RequestStatus.STATUS_WAITING;
import static com.ctey.cpstatic.Enum.RequestStatus.STATUS_WORKING;
import static com.ctey.cpstatic.Static.CPCoreStatic.*;
import static com.ctey.cpstatic.Static.CPUserStatic.CP_INIT_SCHEDULE;
import static com.ctey.cpstatic.Static.CPUserStatic.CP_WAIT_SIZE;

// 数据库连接池动态参数和信息存储上下文
@Component
public class CPContext {
    private final ScheduledExecutorService cpExecutorExamineTask;
    private final DataSourceModule dataSourceModule;
    private final UUIDModule uuidModule;

    @Autowired
    public CPContext(ScheduledExecutorService cpExecutorExamineTask, DataSourceModule dataSourceModule, UUIDModule uuidModule) {
        this.cpExecutorExamineTask = cpExecutorExamineTask;
        this.dataSourceModule = dataSourceModule;
        this.uuidModule = uuidModule;
    }

    // 客户端线程等待空闲连接的信号量
    public static final Semaphore IDLE_POOL_SEMAPHORE = new Semaphore(0, true);
    // 连接池当前的存活连接数
    public static final AtomicInteger CURRENT_POOL_SIZE = new AtomicInteger(0);
    // 连接池当前的空闲连接数
    public static final AtomicInteger CURRENT_IDLE_SIZE = new AtomicInteger(0);

    // 连接池当前的存活连接记录
    public final ConcurrentHashMap<String, ConnectionEntity> connectionEntityPoolMap = new ConcurrentHashMap<>();
    // 连接池历史创建的连接记录
    public final ConcurrentHashMap<String, ConnectionEntity> connectionEntityHistoryMap = new ConcurrentHashMap<>();
    // 客户端线程的历史任务记录
    public final ConcurrentHashMap<String, RequestEntity> requestEntityHistoryMap = new ConcurrentHashMap<>();
    // 客户端线程等待空闲连接的发送信号队列
    public final BlockingQueue<Integer> requestLackConnectionQueue = new LinkedBlockingQueue<>();
    // 连接池的空闲连接优先级队列(按连接的创建时间排序连接获取的优先级)
    public final BlockingQueue<ConnectionEntity> connectionIdleQueue = new PriorityBlockingQueue<>(MIN_IDLE_SIZE, new Comparator<ConnectionEntity>() {
        @Override
        public int compare(ConnectionEntity o1, ConnectionEntity o2) {
            // negative integer -> the first argument is less
            // positive integer -> the first argument is greater
            // Function<ConnectionStatus, Integer> statusToInt = (status) -> {
            //     if (status == ConnectionStatus.STATUS_IDLE) { return 3; }
            //     else if (status == ConnectionStatus.STATUS_WORKING) { return 2; }
            //     else if (status == ConnectionStatus.STATUS_RESTARTING) { return 1; }
            //     return 0;
            // };
            return Long.compare(o1.getStart(), o2.getStart());
        }
    });

    /*
     * initCPContext()
     * 使用预设参数初始化连接池的空闲连接
     * @return
     * @Date: 2025/1/8 20:44
     */
    @PostConstruct
    public void initCPContext() {
        cpExecutorExamineTask.schedule(() -> {
            try {
                while (!CURRENT_IDLE_SIZE.compareAndSet(0, INITIAL_POOL_SIZE)) {}
                while (!CURRENT_POOL_SIZE.compareAndSet(0, INITIAL_POOL_SIZE)) {}
                for (int i = 0; i < MIN_POOL_SIZE; i++) {
                    String uuid = uuidModule.getUUIDStr();
                    Connection connection = dataSourceModule.getConnection();
                    ConnectionEntity connectionEntity = ModelInitUtil.InitConnection(uuid, connection);
                    connectionEntityPoolMap.put(uuid, connectionEntity);
                    connectionEntityHistoryMap.put(uuid, connectionEntity);
                    connectionIdleQueue.add(connectionEntity);
                }
            } catch (Exception ex) { MessagePrintUtil.printException(ex); }
        }, CP_INIT_SCHEDULE, TimeUnit.SECONDS);
    }

    /*
     * createConnection()
     * 创建新连接(不考虑参数限制问题)
     * @return
     * @Date: 2025/1/7 21:32
     */
    public ConnectionEntity createConnection() throws Exception{
        String uuid = uuidModule.getUUIDStr();
        Connection connection = dataSourceModule.getConnection();
        return ModelInitUtil.InitConnection(uuid, connection);
    }

}
