package net.robinfriedli.aiode.persist.customchange;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

public abstract class InsertEnumLookupValuesChange<T extends Enum<T>> implements CustomTaskChange {

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection connection = (JdbcConnection) database.getConnection();
        T[] values = getValues();
        String tableName = getTableName();

        try {
            for (T value : values) {
                PreparedStatement preparedStatement = connection.prepareStatement(String.format("SELECT count(*) from %s WHERE unique_id = ?", tableName));
                preparedStatement.setString(1, value.name());
                ResultSet resultSet = preparedStatement.executeQuery();
                if (!resultSet.next()) {
                    throw new IllegalStateException("no result returned by count query");
                }
                long count = resultSet.getLong(1);
                if (count < 1) {
                    PreparedStatement insertStatement = connection.prepareStatement(String.format("INSERT INTO %s (unique_id) VALUES (?)", tableName));
                    insertStatement.setString(1, value.name());
                    int affected = insertStatement.executeUpdate();

                    Logger logger = LoggerFactory.getLogger(getClass());
                    logger.info(String.format("inserted %d rows into table %s", affected, tableName));
                }
            }
        } catch (DatabaseException | SQLException e) {
            throw new CustomChangeException(e);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return String.format("updated contents of table %s according to enum values", getTableName());
    }

    @Override
    public void setUp() throws SetupException {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }

    protected abstract T[] getValues();

    protected abstract String getTableName();

}
