package hexlet.code.repository;

import hexlet.code.model.UrlCheck;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

public final class UrlCheckRepository extends BaseRepository {
    public UrlCheckRepository(DataSource dataSource) {
        super(dataSource);
    }

    public UrlCheck save(UrlCheck urlCheck) throws SQLException {
        String sql = """
            INSERT INTO url_checks (url_id, status_code, h1, title, description, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setLong(1, urlCheck.urlId());
            statement.setInt(2, urlCheck.statusCode());
            statement.setString(3, urlCheck.h1());
            statement.setString(4, urlCheck.title());
            statement.setString(5, urlCheck.description());
            statement.setTimestamp(6, urlCheck.createdAt());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return new UrlCheck(
                        generatedKeys.getLong(1),
                        urlCheck.statusCode(),
                        urlCheck.title(),
                        urlCheck.h1(),
                        urlCheck.description(),
                        urlCheck.urlId(),
                        urlCheck.createdAt()
                    );
                }
            }
        }

        throw new SQLException("Failed to save URL check");
    }

    public List<UrlCheck> findByUrlId(long urlId) throws SQLException {
        String sql = """
            SELECT id, url_id, status_code, h1, title, description, created_at
            FROM url_checks
            WHERE url_id = ?
            ORDER BY created_at DESC, id DESC
            """;
        List<UrlCheck> checks = new ArrayList<>();

        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, urlId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    checks.add(buildUrlCheck(resultSet));
                }
            }
        }

        return checks;
    }

    public Map<Long, UrlCheck> findLatestChecks() throws SQLException {
        String sql = """
            SELECT id, url_id, status_code, h1, title, description, created_at
            FROM url_checks
            ORDER BY url_id ASC, created_at DESC, id DESC
            """;
        Map<Long, UrlCheck> latestChecks = new HashMap<>();

        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                UrlCheck urlCheck = buildUrlCheck(resultSet);
                latestChecks.putIfAbsent(urlCheck.urlId(), urlCheck);
            }
        }

        return latestChecks;
    }

    private UrlCheck buildUrlCheck(ResultSet resultSet) throws SQLException {
        return new UrlCheck(
            resultSet.getLong("id"),
            resultSet.getInt("status_code"),
            resultSet.getString("title"),
            resultSet.getString("h1"),
            resultSet.getString("description"),
            resultSet.getLong("url_id"),
            resultSet.getTimestamp("created_at")
        );
    }
}
