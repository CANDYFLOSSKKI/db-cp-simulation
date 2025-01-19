package com.ctey.cpstatic.Entity;

import com.ctey.cpstatic.Enum.ReqStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 客户端线程任务实体类
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReqEntity {
    private String UUID;
    private Long arrive;
    private Long acquire;
    private Long release;
    private ReqWork work;
    private ReqStatus status;
}
