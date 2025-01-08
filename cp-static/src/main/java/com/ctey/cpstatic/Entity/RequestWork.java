package com.ctey.cpstatic.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 客户端任务细节(Controller传参)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestWork implements Serializable {
    private Integer id;
    private Long arrive;
    private Long keep;
}
