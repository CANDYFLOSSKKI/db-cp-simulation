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

### 项目功能

连接池默认存在一定量的空闲连接，空闲连接可被客户端线程获取变为工作状态，连接池初始化的同时设置下列三个守护线程（定时任务）

- **processMaxWorkTimeConnection()**: 监控客户端线程是否持有某个连接的时间过长，强制关闭此连接
- **processMinIdleConnection()**: 监控保持连接池内部始终有不小于MIN_IDLE_SIZE个数的空闲连接，当空闲连接被客户端线程占用时，该任务会自动创建新的空闲连接
- **processMaxIdleConnection()**: 监控是否存在空闲时间过长的空闲连接，删除超过MIN_IDLE_SIZE部分的空闲连接，重置剩余空闲连接的状态（内部重新获取数据库连接）

客户端线程允许自定义到达时间与连接池并发交互，同时只会有一个客户端线程尝试获取连接，获取连接失败后向连接池发送创建新连接请求，同时循环阻塞等待新空闲连接，超时释放该线程视为超时。客户端线程获取线程后，允许自定义其保存该连接的时间（模拟实际任务时长），最后归还连接给连接池

连接池接收到客户端线程创建新连接的请求后，如果当前连接数小于MAX_POOL_SIZE，则创建新的空闲连接，通过信号量通知等待时间最长的客户端线程获取连接

连接池接收到客户端线程归还的连接后，判断其引用次数是否到达规定的上限，并且通过SELECT 1测试查询检测连接健康度，未通过的连接将直接被关闭，检查无误的连接重新放入空闲连接中，通过信号量通知此时还在等待的客户端线程获取连接

目前已实现下列除最大生命周期外的变量参数设置与相关交互逻辑

| 初始化连接数         | 连接池初始创建的连接数                                       |
| :------------------- | :----------------------------------------------------------- |
| 最大连接数           | 连接池允许的最大连接数                                       |
| 最小连接数           | 连接池保持的最小连接数                                       |
| 最长等待时间         | 客户端获取连接等待的最长时间，超过时间放弃获取连接并抛出异常 |
| **最小空闲连接数**   | 连接池中保持的最小空闲连接数；<br>客户端获取连接时优先分配这些空闲连接，后台异步创建新连接保证空闲连接数量<br>（到达最大连接数后只会分配） |
| **最大空闲时间**     | 连接在连接池中保持空闲的最长时间，超过时间的连接会被关闭；<br>用于释放长时间的空闲连接资源，如果关闭后小于最小空闲连接数后台会创建新连接 |
| **最大生命周期**     | （暂未实现）连接在连接池中存活的最长时间，超时的连接会被关闭并替换；<br>通常用于定期替换连接来确保连接的健康，因此关闭后会立即创建新连接 |
| **连接泄露检测阈值** | 连接一定时间内未被归还到线程池时，可能导致资源泄露，新请求无法获得连接；<br>强行关闭连接以防止资源耗尽 |

### 项目使用

成功配置数据源并启动项目后，项目默认运行在本地的13801端口上，输出日志默认保存在/app-cp-logs中，可使用以下两个预设的HTTP接口进行交互

**POST /api/v1/start** 启动客户端任务模拟（可多次发送模拟并发效果），数据格式示例如下：

```json
{
   "count": 0,       // 任务列表个数(暂未使用)
   "workList": [     // 任务列表明细
     {
       "id": 0,      // 任务序号(暂未使用)
       "arrive": 0,  // 到达时间(从发送时间开始,首次尝试获取连接的时间戳时延)
       "keep": 0     // 保持时间(从成功获取到连接开始,归还连接给连接池的时间戳时延)
     }
   ]
}
```

**GET /api/v1/examine** 输出连接池当前所有存活连接及其状态，可改写com.ctey.cpmodule.Service.CPHandlerService.outPutCPExamine()自定义输出结果

服务运行输出

![sample-output](https://github.com/CANDYFLOSSKKI/db-jdbc-cp/raw/main/public/sample-output.png)

测试接口输出

![sample-test-interface](https://github.com/CANDYFLOSSKKI/db-jdbc-cp/raw/main/public/sample-test-interface.png)

### 相关背景参考

执行SQL的过程中，客户端和数据库服务主要通过TCP协议建立和关闭连接，默认情况下数据库会为每个连接创建新线程，在连接关闭时销毁该线程（在MySQL中称为`one-thread-per-connection`模式，在Oracle中称为`dedicated server`专用服务器模式）

**从连接的角度上看**，SQL的执行过程会引入额外的网络往返开销（TCP的三次握手建立连接和四次挥手关闭连接），客户端频繁创建和关闭连接会使得数据库服务负载较高，响应时间较长

TCP关闭连接时，客户端最后还需要等待2MSL（两个最大数据段生命周期）时间才会进入CLOSED状态，大量处于TIME_WAIT状态的待关闭连接会占用系统的临时端口，新连接需要等待分配端口才能建立连接，使得连接建立时间也会变长

**从线程的角度上看**，系统的资源是有限的。工作线程数量不多，系统资源还未耗尽时，随着连接数的增加，系统的吞吐量随之增加，此时由于线程对系统资源的竞争程度逐渐增加，系统对各个连接的响应时间也会增加

当连接数超过了某个耗尽系统资源的临界点后，CPU 时间片在大量线程间频繁调度，不同线程上下文频繁切换，徒增系统开销，数据库整体的数据吞吐量反而会降低

如果客户端的连接数并不多，为每个连接单独创建线程的方式也存在着一定的优势，部分工作线程阻塞在耗时较长的慢查询时，系统仍然可以通过创建线程快速响应新的请求

**连接池**是负责分配，管理数据库连接对象的容器，连接池在内部对象池中维护一定量的数据库连接，并对外暴露数据库获取连接和返回方法，提供一套高效的连接分配和使用策略实现连接的复用，提高服务效率。客户端连接数据库服务器时可以从连接池获取空闲连接，使用完毕后将连接对象归还给连接池，省略了创建和销毁连接的过程

**客户端从连接池获取连接**

1. 客户端向连接池发送获取连接的请求，如果连接池有空闲连接，优先分配空闲连接给客户端
2. 没有空闲连接时，如果连接数未到达限定的最大连接数，则新建连接分配给客户端
3. 没有空闲连接时，如果连接数已到达限定的最大连接数，则进入等待队列排队等待，直到有连接可用，或超过限定的最大等待时间后超时

**客户端向连接池归还连接**

1. 客户端将连接归还给连接池，连接池对连接进行健康检查（通过发送测试查询SELECT 1等方式确认连接正常，某些连接池还会维护连接的使用次数，超过使用次数的也视为不健康的连接）
2. 回滚或提交连接在使用过程中未提交的事务，移除数据库会话范围内的临时数据和缓存，释放期间获取的任何锁或同步资源
3. 更新连接状态为空闲连接并返回线程池，如果有等待连接的请求则通知其获取连接

在客户端多线程并发的情况下，需要保证连接管理自身数据的一致性和连接内部数据的一致性，可以使用同步机制控制多个线程对连接池的访问，确保连接的分配和归还过程是线程安全的，同步机制还可用于保证连接资源分配的公平性
在客户端需要事务支持的情况下，可以采用显式的事务支撑方法，每一个事务独占一个连接，通过动态生成的事务注册表登记事务发起者和事务使用的连接的对应关系，通过该表隔离使用事务的部分和连接管理的部分，直接查找该注册表使用已经分配的连接来完成事务，连接在该事务运行中不能被复用，保证了事务的ACID特性（原子性、一致性、隔离性和持久性）

在连接池相关参数的设置上，如果连接池的连接数设置过小，在客户端业务量突增时可能短时间内产生连接风暴；如果最小空闲连接数设置过大，数据库连接过剩，部分连接会进入超出空闲时间被销毁后又被创建的循环中，浪费系统资源；如果最大连接数设置过大，大量连接可能超出数据库的处理能力，进而导致业务受到影响

