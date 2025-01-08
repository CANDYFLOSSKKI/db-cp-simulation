package com.ctey.cpstatic.Entity;

import com.ctey.cpstatic.Enum.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestEntity {
    private String UUID;
    private Long arrive;
    private Long acquire;
    private Long release;
    private RequestWork work;
    private RequestStatus status;
}
