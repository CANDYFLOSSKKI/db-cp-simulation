package com.ctey.cpmodule.Module;


import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
