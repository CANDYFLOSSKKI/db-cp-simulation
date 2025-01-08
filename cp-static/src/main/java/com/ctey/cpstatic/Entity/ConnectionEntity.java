package com.ctey.cpstatic.Entity;


import com.ctey.cpstatic.Enum.ConnectionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Connection;

// 连接实体类
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConnectionEntity {
    private String UUID;
    private Connection connection;
    private Long start;
    private Long lastWork;
    private Long lastRelease;
    private Integer count;
    private RequestEntity request;
    private ConnectionStatus status;
}
