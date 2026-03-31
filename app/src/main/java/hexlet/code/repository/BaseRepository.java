package hexlet.code.repository;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public abstract class BaseRepository {
    protected final DataSource dataSource;

    protected BaseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
