package net.robinfriedli.aiode.persist.customchange;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

public class RestrictCommandAccessChange implements CustomTaskChange {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String commandId;

    @Override
    public void execute(Database database) throws CustomChangeException {
        if (Strings.isNullOrEmpty(commandId)) {
            throw new CustomChangeException("commandId not set");
        }

        JdbcConnection connection = (JdbcConnection) database.getConnection();

        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                "INSERT INTO access_configuration(permission_identifier, fk_guild_specification, fk_permission_type) " +
                    "SELECT ?, pk AS fk_guild_specification, (SELECT pk FROM permission_type WHERE unique_id = 'COMMAND') AS fk_permission_type " +
                    "FROM guild_specification AS gs " +
                    "WHERE NOT EXISTS(SELECT * FROM access_configuration WHERE fk_guild_specification = gs.pk AND permission_identifier = ?)"
            );
            preparedStatement.setString(1, commandId);
            preparedStatement.setString(2, commandId);
            int rows = preparedStatement.executeUpdate();
            logger.info(String.format("Inserted %d access_configurations for command %s", rows, commandId));
        } catch (DatabaseException | SQLException e) {
            throw new CustomChangeException(e);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "successfully restrict command access for " + commandId;
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

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }
}
