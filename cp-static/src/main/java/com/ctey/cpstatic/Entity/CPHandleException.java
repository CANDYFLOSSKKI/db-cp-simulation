package com.ctey.cpstatic.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 自定义操作异常时返回操作的状态码和消息信息
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CPHandleException {
    private String code;
    private String msg;
}
