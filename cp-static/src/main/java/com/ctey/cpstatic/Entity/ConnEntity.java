package com.ctey.cpstatic.Entity;


import com.ctey.cpstatic.Enum.ConnStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Connection;

// 连接实体类
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConnEntity {
    private String UUID;
    private Connection connection;
    private Long start;
    private Long lastWork;
    private Long lastRelease;
    private Integer count;
    private ReqEntity request;
    private ConnStatus status;
}
