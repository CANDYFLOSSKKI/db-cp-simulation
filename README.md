# db-jdbc-cp
 A Simple DB Connection Pooling Simulation Service/简单的数据库连接池模拟

Java version: 17

Gradle Version: 8.11.1

### 项目结构

- **cp-main**: 服务入口，日志/线程池/端口相关参数配置
- **cp-module**: 连接池和客户端线程任务模拟
- **cp-static**: 连接池自定义参数配置，相关数据交互和状态类型
- **cp-web**: 命令入口

### 项目配置

可使用项目自带的MySQL数据源，也可以自定义数据源（修改对应数据源连接的获取方式为单例类，重写com.ctey.cpmodule.Module.DataSourceModule类）

使用项目自带的MySQL数据源前，修改com.ctey.cpstatic.Static.DataSourceStatic下的URL，用户名和密码等信息，以便可以成功连接到数据库

配置客户端线程/守护线程相关的线程池参数在com.ctey.cpmodule.Config.DTPConfig类，使用了

[dynamic-tp](https://github.com/dromara/dynamic-tp)组件搭建便于支持定时任务的线程池，后续还可以集成监控和动态参数功能

配置连接池核心参数在com.ctey.cpstatic.Static.CPCoreStatic类

### 项目使用

成功配置数据源并启动项目后，项目默认运行在本地的13801端口上，输出日志默认保存在/app-cp-logs中，可使用以下两个接口进行交互

**POST /api/v1/start** 启动客户端任务模拟（可多次发送模拟并发效果），数据格式示例已包含在代码中

**GET /api/v1/examine** 输出连接池当前所有存活连接及其状态，可改写com.ctey.cpmodule.Service.CPHandlerService.outPutCPExamine()自定义输出结果

### 项目功能

连接池默认存在一定量的空闲连接，空闲连接可被客户端线程获取变为工作状态，连接池初始化的同时设置下列三个守护线程（定时任务）

- **processMaxWorkTimeConnection()**: 监控客户端线程是否持有某个连接的时间过长，强制关闭此连接
- **processMinIdleConnection()**: 监控保持连接池内部始终有不小于MIN_IDLE_SIZE个数的空闲连接，当空闲连接被客户端线程占用时，该任务会自动创建新的空闲连接
- **processMaxIdleConnection()**: 监控是否存在空闲时间过长的空闲连接，删除超过MIN_IDLE_SIZE部分的空闲连接，重置剩余空闲连接的状态（内部重新获取数据库连接）

客户端线程允许自定义到达时间与连接池并发交互，同时只会有一个客户端线程尝试获取连接，获取连接失败后向连接池发送创建新连接请求，同时循环阻塞等待新空闲连接，超时释放该线程视为超时。客户端线程获取线程后，允许自定义其保存该连接的时间（模拟实际任务时长），最后归还连接给连接池

连接池接收到客户端线程创建新连接的请求后，如果当前连接数小于MAX_POOL_SIZE，则创建新的空闲连接，通过信号量通知等待时间最长的客户端线程获取连接

连接池接收到客户端线程归还的连接后，判断其引用次数是否到达规定的上限，并且通过SELECT 1测试查询检测连接健康度，未通过的连接将直接被关闭，检查无误的连接重新放入空闲连接中，通过信号量通知此时还在等待的客户端线程获取连接
