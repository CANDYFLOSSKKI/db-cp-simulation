package com.ctey.cpstatic.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestWork implements Serializable {
    private Integer id;
    private Long arrive;
    private Long keep;
}
