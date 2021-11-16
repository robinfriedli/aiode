package net.robinfriedli.aiode.persist.customchange;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import net.robinfriedli.aiode.audio.Playable;

public class MigratePlaybackHistorySource implements CustomTaskChange {

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection connection = (JdbcConnection) database.getConnection();
        try {
            PreparedStatement queryAll = connection.prepareStatement("SELECT DISTINCT source FROM playback_history");
            ResultSet resultSet = queryAll.executeQuery();

            PreparedStatement spotifyItemKindQuery = connection.prepareStatement("SELECT pk from spotify_item_kind where unique_id = 'TRACK'");
            ResultSet spotifyItemKindResultSet = spotifyItemKindQuery.executeQuery();
            if (!spotifyItemKindResultSet.next()) {
                throw new IllegalStateException("no spotify_item_kind found for id 'TRACK'");
            }
            long spotifyItemKindTrackPk = spotifyItemKindResultSet.getLong(1);

            while (resultSet.next()) {
                String source = resultSet.getString(1);

                switch (source) {
                    case "Spotify":
                        String spotifySourceId = Playable.Source.SPOTIFY.name();
                        long spotifyPlaybackSourcePk = queryPlaybackSourcePk(spotifySourceId, connection);

                        PreparedStatement spotifyUpdateStatement = connection.prepareStatement("UPDATE playback_history SET fk_source = ?, fk_spotify_item_kind = ? where source = ?");
                        spotifyUpdateStatement.setLong(1, spotifyPlaybackSourcePk);
                        spotifyUpdateStatement.setLong(2, spotifyItemKindTrackPk);
                        spotifyUpdateStatement.setString(3, "Spotify");
                        spotifyUpdateStatement.executeUpdate();
                        continue;
                    case "YouTube":
                        String youTubeSourceId = Playable.Source.YOUTUBE.name();
                        long youTubePlaybackSourcePk = queryPlaybackSourcePk(youTubeSourceId, connection);

                        PreparedStatement youTubeUpdateStatement = connection.prepareStatement("UPDATE playback_history SET fk_source = ? where source = ?");
                        youTubeUpdateStatement.setLong(1, youTubePlaybackSourcePk);
                        youTubeUpdateStatement.setString(2, "YouTube");
                        youTubeUpdateStatement.executeUpdate();
                        continue;
                    case "Url":
                        String urlSourceId = Playable.Source.URL.name();
                        long urlPlaybackSourcePk = queryPlaybackSourcePk(urlSourceId, connection);

                        PreparedStatement urlUpdateStatement = connection.prepareStatement("UPDATE playback_history SET fk_source = ? where source = ?");
                        urlUpdateStatement.setLong(1, urlPlaybackSourcePk);
                        urlUpdateStatement.setString(2, "Url");
                        urlUpdateStatement.executeUpdate();
                        continue;
                    default:
                        throw new IllegalStateException("unsupported source: " + source);
                }
            }
        } catch (DatabaseException | SQLException e) {
            throw new CustomChangeException(e);
        }
    }

    private long queryPlaybackSourcePk(String sourceId, JdbcConnection connection) {
        try {
            PreparedStatement sourceQuery = connection.prepareStatement("SELECT pk FROM playback_history_source where unique_id = ?");
            sourceQuery.setString(1, sourceId);
            ResultSet sourceResultSet = sourceQuery.executeQuery();

            if (!sourceResultSet.next()) {
                throw new IllegalStateException("no playback_history_source entity found for id " + sourceId);
            }

            return sourceResultSet.getLong(1);
        } catch (DatabaseException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "successfully migrated playback history";
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
}
