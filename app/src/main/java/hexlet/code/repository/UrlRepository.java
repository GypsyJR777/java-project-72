package hexlet.code.repository;

import hexlet.code.exception.DatabaseException;
import hexlet.code.model.Url;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class UrlRepository extends BaseRepository {
    public UrlRepository(DataSource dataSource) {
        super(dataSource);
    }

    public Optional<Url> find(long id) throws DatabaseException {
        String sql = "SELECT id, name, created_at FROM urls WHERE id = ?";
        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(buildUrl(resultSet));
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find URL by id", e);
        }
    }

    public Optional<Url> findByName(String name) throws DatabaseException {
        String sql = "SELECT id, name, created_at FROM urls WHERE name = ?";
        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(buildUrl(resultSet));
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to find URL by name", e);
        }
    }

    public List<Url> findAll() throws DatabaseException {
        String sql = "SELECT id, name, created_at FROM urls ORDER BY created_at DESC, id DESC";
        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {
            List<Url> urls = new ArrayList<>();

            while (resultSet.next()) {
                urls.add(buildUrl(resultSet));
            }

            return urls;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load URLs", e);
        }
    }

    public Optional<Url> save(Url url) throws DatabaseException {
        String sql = "INSERT INTO urls (name, created_at) VALUES (?, ?)";
        try (
            Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            statement.setString(1, url.name());
            statement.setTimestamp(2, Timestamp.from(url.createdAt()));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return Optional.of(new Url(generatedKeys.getLong(1), url.name(), url.createdAt()));
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to save URL", e);
        }
    }

    private Url buildUrl(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        String name = resultSet.getString("name");
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new Url(id, name, createdAt.toInstant());
    }
}
