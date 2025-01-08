package com.ctey.cpstatic.Static;

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
}
