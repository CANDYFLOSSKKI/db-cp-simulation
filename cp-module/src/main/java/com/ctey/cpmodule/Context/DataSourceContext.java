package com.ctey.cpmodule.Context;

import com.ctey.cpstatic.Static.DataSourceStatic;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

// 数据源单例管理类
@Component
public class DataSourceContext {
    private static volatile MysqlDataSource DATASOURCE;

    public MysqlDataSource getInstance() {
        if (DATASOURCE == null) {
            synchronized (DataSourceContext.class) {
                if (DATASOURCE == null) {
                    DATASOURCE = new MysqlDataSource();
                    DATASOURCE.setUrl(DataSourceStatic.DATASOURCE_URL);
                    DATASOURCE.setUser(DataSourceStatic.DATASOURCE_USER);
                    DATASOURCE.setPassword(DataSourceStatic.DATASOURCE_PASSWD);
                }
            }
        }
        return DATASOURCE;
    }

    public Connection getConnection() throws SQLException {
        return getInstance().getConnection();
    }

}
