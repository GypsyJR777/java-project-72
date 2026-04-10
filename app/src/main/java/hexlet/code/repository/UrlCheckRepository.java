package hexlet.code.repository;

import hexlet.code.exception.DatabaseException;
import hexlet.code.model.UrlCheck;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

public final class UrlCheckRepository extends BaseRepository {
    public UrlCheckRepository(DataSource dataSource) {
        super(dataSource);
    }

    public Optional<UrlCheck> save(UrlCheck urlCheck) throws DatabaseException {
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
            statement.setTimestamp(6, Timestamp.from(urlCheck.createdAt()));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return Optional.of(new UrlCheck(
                        generatedKeys.getLong(1),
                        urlCheck.statusCode(),
                        urlCheck.title(),
                        urlCheck.h1(),
                        urlCheck.description(),
                        urlCheck.urlId(),
                        urlCheck.createdAt()
                    ));
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to save URL check", e);
        }
    }

    public List<UrlCheck> findByUrlId(long urlId) throws DatabaseException {
        String sql = """
            SELECT id, url_id, status_code, h1, title, description, created_at
            FROM url_checks
            WHERE url_id = ?
            ORDER BY created_at DESC, id DESC
            """;
        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            List<UrlCheck> checks = new ArrayList<>();

            statement.setLong(1, urlId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    checks.add(buildUrlCheck(resultSet));
                }
            }

            return checks;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load URL checks", e);
        }
    }

    public Map<Long, UrlCheck> findLatestChecks() throws DatabaseException {
        String sql = """
            SELECT id, url_id, status_code, h1, title, description, created_at
            FROM url_checks
            ORDER BY url_id ASC, created_at DESC, id DESC
            """;
        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {
            Map<Long, UrlCheck> latestChecks = new HashMap<>();

            while (resultSet.next()) {
                UrlCheck urlCheck = buildUrlCheck(resultSet);
                latestChecks.putIfAbsent(urlCheck.urlId(), urlCheck);
            }

            return latestChecks;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load latest URL checks", e);
        }
    }

    private UrlCheck buildUrlCheck(ResultSet resultSet) throws SQLException {
        return new UrlCheck(
            resultSet.getLong("id"),
            resultSet.getInt("status_code"),
            resultSet.getString("title"),
            resultSet.getString("h1"),
            resultSet.getString("description"),
            resultSet.getLong("url_id"),
            resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
