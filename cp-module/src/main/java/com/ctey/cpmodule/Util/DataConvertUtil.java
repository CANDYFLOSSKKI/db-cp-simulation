package com.ctey.cpmodule.Util;

import cn.hutool.core.date.DateTime;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DataConvertUtil {

    /*
     * TimestampToDateTime()
     * 时间戳转输出时的日期时间信息
     * @return
     * @Date: 2025/1/8 21:24
     */
    public static String TimestampToDateTime(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
