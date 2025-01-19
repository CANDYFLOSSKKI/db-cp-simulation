package com.ctey.cpmodule.Context;

import com.ctey.cpmodule.Util.MsgPrintUtil;
import com.ctey.cpstatic.Entity.ConnEntity;
import com.ctey.cpstatic.Entity.ReqEntity;
import com.ctey.cpstatic.Util.EntityInitUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.ctey.cpstatic.Static.CPCoreStatic.*;
import static com.ctey.cpstatic.Static.CPUserStatic.CP_INIT_SCHEDULE;

// 数据库连接池动态参数和信息存储上下文
@Component
public class CPContext {
    private final ScheduledExecutorService cpExecutorExamineTask;
    private final DataSourceContext dataSourceContext;
    private final UUIDContext uuidContext;

    @Autowired
    public CPContext(ScheduledExecutorService cpExecutorExamineTask, DataSourceContext dataSourceContext, UUIDContext uuidContext) {
        this.cpExecutorExamineTask = cpExecutorExamineTask;
        this.dataSourceContext = dataSourceContext;
        this.uuidContext = uuidContext;
    }

    // 标志位,指示当前连接池是否处于运行状态
    public static final AtomicBoolean CP_IS_RUNNING = new AtomicBoolean(true);
    // 用户操作关闭/重启连接池的同步锁,同时只能执行一个操作
    public static final ReentrantLock CP_HANDLE_LOCK = new ReentrantLock();

    // 客户端线程等待空闲连接的信号量
    public static final Semaphore IDLE_POOL_SEMAPHORE = new Semaphore(0, true);
    // 连接池当前的存活连接数
    public static final AtomicInteger CURRENT_POOL_SIZE = new AtomicInteger(0);
    // 连接池当前的空闲连接数
    public static final AtomicInteger CURRENT_IDLE_SIZE = new AtomicInteger(0);

    // 连接池当前的存活连接记录
    public final ConcurrentHashMap<String, ConnEntity> connectionEntityPoolMap = new ConcurrentHashMap<>();
    // 连接池历史创建的连接记录
    public final ConcurrentHashMap<String, ConnEntity> connectionEntityHistoryMap = new ConcurrentHashMap<>();
    // 客户端线程的历史任务记录
    public final ConcurrentHashMap<String, ReqEntity> requestEntityHistoryMap = new ConcurrentHashMap<>();
    // 客户端线程等待空闲连接的发送信号队列
    public final BlockingQueue<Integer> requestLackConnectionQueue = new LinkedBlockingQueue<>();
    // 连接池的空闲连接优先级队列(按连接的创建时间排序连接获取的优先级)
    public final BlockingQueue<ConnEntity> connectionIdleQueue = new PriorityBlockingQueue<>(MIN_IDLE_SIZE, new Comparator<ConnEntity>() {
        @Override
        public int compare(ConnEntity o1, ConnEntity o2) {
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
                    String uuid = uuidContext.getUUIDStr();
                    Connection connection = dataSourceContext.getConnection();
                    ConnEntity connEntity = EntityInitUtil.InitConnection(uuid, connection);
                    connectionEntityPoolMap.put(uuid, connEntity);
                    connectionEntityHistoryMap.put(uuid, connEntity);
                    connectionIdleQueue.add(connEntity);
                }
            } catch (Exception ex) { MsgPrintUtil.printException(ex); }
        }, CP_INIT_SCHEDULE, TimeUnit.SECONDS);
    }

    /*
     * createConnection()
     * 创建新连接(不考虑参数限制问题)
     * @return
     * @Date: 2025/1/7 21:32
     */
    public ConnEntity createConnection() throws Exception{
        String uuid = uuidContext.getUUIDStr();
        Connection connection = dataSourceContext.getConnection();
        return EntityInitUtil.InitConnection(uuid, connection);
    }

}
