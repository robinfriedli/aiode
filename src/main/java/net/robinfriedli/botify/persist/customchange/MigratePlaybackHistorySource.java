package net.robinfriedli.botify.persist.customchange;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import net.robinfriedli.botify.audio.Playable;

public class MigratePlaybackHistorySource implements CustomTaskChange {

    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection connection = (JdbcConnection) database.getConnection();
        try {
            PreparedStatement queryAll = connection.prepareStatement("SELECT pk, source FROM playback_history");
            ResultSet resultSet = queryAll.executeQuery();

            PreparedStatement spotifyItemKindQuery = connection.prepareStatement("SELECT pk from spotify_item_kind where unique_id = 'TRACK'");
            ResultSet spotifyItemKindResultSet = spotifyItemKindQuery.executeQuery();
            if (!spotifyItemKindResultSet.next()) {
                throw new IllegalStateException("no spotify_item_kind found for id 'TRACK'");
            }
            long spotifyItemKindTrackPk = spotifyItemKindResultSet.getLong(1);

            Map<String, Long> playbackSourceMap = new HashMap<>();
            while (resultSet.next()) {
                long pk = resultSet.getLong(1);
                String source = resultSet.getString(2);

                switch (source) {
                    case "Spotify":
                        String spotifySourceId = Playable.Source.SPOTIFY.name();
                        long spotifyPlaybackSourcePk = getOrQueryPlaybackSourcePk(spotifySourceId, playbackSourceMap, connection);

                        PreparedStatement spotifyUpdateStatement = connection.prepareStatement("UPDATE playback_history SET fk_source = ?, fk_spotify_item_kind = ? where pk = ?");
                        spotifyUpdateStatement.setLong(1, spotifyPlaybackSourcePk);
                        spotifyUpdateStatement.setLong(2, spotifyItemKindTrackPk);
                        spotifyUpdateStatement.setLong(3, pk);
                        spotifyUpdateStatement.executeUpdate();
                        continue;
                    case "YouTube":
                        String youTubeSourceId = Playable.Source.YOUTUBE.name();
                        long youTubePlaybackSourcePk = getOrQueryPlaybackSourcePk(youTubeSourceId, playbackSourceMap, connection);

                        PreparedStatement youTubeUpdateStatement = connection.prepareStatement("UPDATE playback_history SET fk_source = ? where pk = ?");
                        youTubeUpdateStatement.setLong(1, youTubePlaybackSourcePk);
                        youTubeUpdateStatement.setLong(2, pk);
                        youTubeUpdateStatement.executeUpdate();
                        continue;
                    case "Url":
                        String urlSourceId = Playable.Source.URL.name();
                        long urlPlaybackSourcePk = getOrQueryPlaybackSourcePk(urlSourceId, playbackSourceMap, connection);

                        PreparedStatement urlUpdateStatement = connection.prepareStatement("UPDATE playback_history SET fk_source = ? where pk = ?");
                        urlUpdateStatement.setLong(1, urlPlaybackSourcePk);
                        urlUpdateStatement.setLong(2, pk);
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

    private long getOrQueryPlaybackSourcePk(String sourceId, Map<String, Long> playbackSourceMap, JdbcConnection connection) {
        return playbackSourceMap.computeIfAbsent(sourceId, id -> {
            try {
                PreparedStatement sourceQuery = connection.prepareStatement("SELECT pk FROM playback_history_source where unique_id = ?");
                sourceQuery.setString(1, id);
                ResultSet sourceResultSet = sourceQuery.executeQuery();

                if (!sourceResultSet.next()) {
                    throw new IllegalStateException("no playback_history_source entity found for id " + id);
                }

                return sourceResultSet.getLong(1);
            } catch (DatabaseException | SQLException e) {
                throw new RuntimeException(e);
            }
        });
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
