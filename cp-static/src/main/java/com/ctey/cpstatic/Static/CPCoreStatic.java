package com.ctey.cpstatic.Static;

public class CPCoreStatic {
    // 初始连接数量
    public static final Integer INITIAL_POOL_SIZE = 4;
    // 最大连接数量
    public static final Integer MAX_POOL_SIZE = 10;
    //最小连接数量
    public static final Integer MIN_POOL_SIZE = 4;
    // 最小空闲连接数
    public static final Integer MIN_IDLE_SIZE = 4;
    // 连接超时时间
    public static final Long MAX_WAIT_TIME = 5000L;

    // 连接最大使用次数
    public static final Integer MAX_USE_COUNT = 20;

    // 连接泄露检测
    public static final Long MAX_CONNECT_TIME = 60000L;
    // 最大空闲时间
    public static final Long MAX_IDLE_TIME = 30000L;
    // 最大生命周期
    public static final Long MAX_LIFE_TIME = 600000L;
}
