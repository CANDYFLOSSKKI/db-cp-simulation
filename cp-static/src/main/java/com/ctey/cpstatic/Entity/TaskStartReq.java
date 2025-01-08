package com.ctey.cpstatic.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

// 客户端任务(Controller传参)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskStartReq implements Serializable {
    private Integer count;
    private List<RequestWork> workList;
}
