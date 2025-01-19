package com.ctey.cpstatic.Util;

import com.ctey.cpstatic.Entity.ConnEntity;
import com.ctey.cpstatic.Entity.ReqEntity;
import com.ctey.cpstatic.Entity.ReqWork;

import java.sql.Connection;

import static com.ctey.cpstatic.Enum.ConnStatus.STATUS_IDLE;
import static com.ctey.cpstatic.Enum.ReqStatus.STATUS_ARRIVED;

public class EntityInitUtil {

    /*
     * InitConnection()
     * 数据库连接转系统中的连接实体对象
     * @return
     * @Date: 2025/1/8 21:23
     */
    public static ConnEntity InitConnection(String UUID, Connection connection) {
        return new ConnEntity(
                UUID,
                connection,
                System.currentTimeMillis(),
                null,
                null,
                0,
                null,
                STATUS_IDLE
        );
    }

    /*
     * InitRequest()
     * 客户端任务转系统中的客户端线程任务实体对象
     * @return
     * @Date: 2025/1/8 21:24
     */
    public static ReqEntity InitRequest(String UUID, ReqWork request) {
        return new ReqEntity(
                UUID,
                System.currentTimeMillis(),
                null,
                null,
                request,
                STATUS_ARRIVED
        );
    }
}
