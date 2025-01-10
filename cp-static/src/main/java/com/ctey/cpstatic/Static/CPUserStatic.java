package com.ctey.cpstatic.Static;

import com.ctey.cpstatic.Entity.CPHandleException;

public class CPUserStatic {
    // 连接池初始化操作的时延
    public static final Integer CP_INIT_SCHEDULE = 3;
    public static final Integer CP_WAIT_SIZE = 10;

    // 客户端线程首次未获取到空闲连接时,向阻塞队列发送的通知消息
    public static final Integer CP_LACK_CONNECTION_SIGN = 1;

    // 连接池相关守护线程(定时任务)的计划时间信息
    public static final Long CP_SAVE_TASK_DELAY = 200L;
    public static final Long CP_SAVE_TASK_DELAY2 = 2000L;
    public static final Long CP_SAVE_TASK_OFFSET2 = 50L;
    public static final Long CP_SAVE_TASK_OFFSET = 200L;

    // 客户端线程获取连接失败处于等待状态时,先前获取连接时间的权重因子
    public static final Double CP_REACQUIRE_FACTOR = 0.35;

    // 自定义操作异常时返回操作的状态码和消息信息
    public static final CPHandleException EXCEPTION_HAS_START = new CPHandleException("501", "CP Service has been started/连接池服务已启动");
    public static final CPHandleException EXCEPTION_HAS_STOP = new CPHandleException("501", "CP Service has been stopped/连接池服务已停止");
    public static final CPHandleException EXCEPTION_LOCKING = new CPHandleException("503", "CP Handler Service has been locked, please try again later/连接池管理程序正在运行中,请稍后再试");
    public static final CPHandleException EXCEPTION_ERROR = new CPHandleException("500", "CP Service inner error/连接池服务内部错误");
}
