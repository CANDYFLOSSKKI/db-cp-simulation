package com.ctey.cpstatic.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskStartReq implements Serializable {
    private Integer count;
    private List<RequestWork> workList;
}
