package me.ryanhamshire.GriefPrevention.database;

import be.garagepoort.mcioc.IocBean;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@IocBean
public class SqlConnectionProvider {
    private HikariDataSource datasource;

    public DataSource getDatasource() {
        if (datasource == null) {
            getDataSource();
        }
        return datasource;
    }

    public Connection getConnection() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            return getDatasource().getConnection();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to connect to the database", e);
        }
    }

    private void getDataSource() {
        if (datasource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + ConfigLoader.databaseHost + ":" + ConfigLoader.databasePort + "/" + ConfigLoader.databaseName + "?autoReconnect=true&&allowMultiQueries=true&allowPublicKeyRetrieval=true");
            config.setUsername(ConfigLoader.databaseUserName);
            config.setPassword(ConfigLoader.databasePassword);
            config.setMaximumPoolSize(10);
            config.setLeakDetectionThreshold(5000);
            config.setAutoCommit(true);
            config.setDriverClassName("com.mysql.jdbc.Driver");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            datasource = new HikariDataSource(config);
        }
    }

}
