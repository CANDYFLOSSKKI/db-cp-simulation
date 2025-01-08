package com.ctey.cpstatic.Util;

import com.ctey.cpstatic.Entity.ConnectionEntity;
import com.ctey.cpstatic.Entity.RequestEntity;
import com.ctey.cpstatic.Entity.RequestWork;
import com.ctey.cpstatic.Enum.RequestStatus;

import java.sql.Connection;

import static com.ctey.cpstatic.Enum.ConnectionStatus.STATUS_IDLE;
import static com.ctey.cpstatic.Enum.RequestStatus.STATUS_ARRIVED;

public class ModelInitUtil {
    public static ConnectionEntity InitConnection(String UUID, Connection connection) {
        return new ConnectionEntity(
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

    public static RequestEntity InitRequest(String UUID, RequestWork request) {
        return new RequestEntity(
                UUID,
                System.currentTimeMillis(),
                null,
                null,
                request,
                STATUS_ARRIVED
        );
    }
}
