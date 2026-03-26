package hexlet.code.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class DatabaseConfig {
    private static final String DEFAULT_JDBC_URL = "jdbc:h2:mem:project;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

    private DatabaseConfig() {
    }

    public static HikariDataSource getDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(resolveJdbcUrl());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    private static String resolveJdbcUrl() {
        String jdbcUrl = System.getenv("JDBC_DATABASE_URL");
        return jdbcUrl == null || jdbcUrl.isBlank() ? DEFAULT_JDBC_URL : jdbcUrl;
    }
}
