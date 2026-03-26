package hexlet.code.database;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

public final class DatabaseInitializer {
    private DatabaseInitializer() {
    }

    public static void run(DataSource dataSource) throws SQLException {
        try (
            Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()
        ) {
            statement.execute(loadSchema());
        }
    }

    private static String loadSchema() {
        try (InputStream input = DatabaseInitializer.class.getClassLoader().getResourceAsStream("schema.sql")) {
            if (input == null) {
                throw new IllegalStateException("schema.sql was not found");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load schema.sql", e);
        }
    }
}
