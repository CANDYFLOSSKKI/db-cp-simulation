package com.ctey.cpmodule.Module;


import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 客户端线程/连接的UUID单例管理类
@Component
public class UUIDModule {
    private static volatile TimeBasedEpochRandomGenerator GENERATOR;

    public TimeBasedEpochRandomGenerator getInstance() {
        if (GENERATOR == null) {
            synchronized (UUIDModule.class) {
                if (GENERATOR == null) {
                    GENERATOR = Generators.timeBasedEpochRandomGenerator();
                }
            }
        }
        return GENERATOR;
    }

    public UUID getUUID() {
        return getInstance().generate();
    }

    public String getUUIDStr() {
        return getInstance().generate().toString();
    }

}
