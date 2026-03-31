package hexlet.code.repository;

import hexlet.code.model.Url;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

public class UrlRepository extends BaseRepository {
    public UrlRepository(DataSource dataSource) {
        super(dataSource);
    }

    public Url save(Url url) throws SQLException {
        String sql = "INSERT INTO urls (name, created_at) VALUES (?, ?)";

        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setString(1, url.name());
            statement.setTimestamp(2, url.createdAt());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new Url(generatedKeys.getLong(1), url.name(), url.createdAt());
                }
            }
        }

        throw new SQLException("Failed to save URL");
    }
}
