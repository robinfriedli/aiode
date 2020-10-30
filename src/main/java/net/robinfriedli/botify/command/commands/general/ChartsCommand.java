package net.robinfriedli.botify.command.commands.general;

import java.math.BigInteger;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.exec.BlockingTrackLoadingExecutor;
import net.robinfriedli.botify.audio.spotify.PlayableTrackWrapper;
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.audio.spotify.SpotifyTrack;
import net.robinfriedli.botify.audio.spotify.SpotifyTrackKind;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Artist;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.entities.PlaybackHistorySource;
import net.robinfriedli.botify.entities.SpotifyItemKind;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.persist.qb.builders.SelectQueryBuilder;
import net.robinfriedli.botify.util.Util;
import org.hibernate.Session;
import org.hibernate.query.Query;

public class ChartsCommand extends AbstractCommand {

    private final PlayableFactory playableFactory;

    public ChartsCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
        playableFactory = Botify.get().getAudioManager().createPlayableFactory(getSpotifyService(), new BlockingTrackLoadingExecutor());
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Guild guild = getContext().getGuild();
        QueryBuilderFactory queryBuilderFactory = getQueryBuilderFactory();

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Date dateAtStartOfMonth = Date.valueOf(startOfMonth.toLocalDate());
        SelectQueryBuilder<PlaybackHistory> trackChartsQuery = queryBuilderFactory
            .select(PlaybackHistory.class,
                (from, cb) -> from.get("fkSource"),
                (from, cb) -> from.get("trackId"),
                (from, cb) -> cb.count(from.get("pk")),
                (from, cb) -> from.get("fkSpotifyItemKind"))
            .where((cb, root) -> cb.isNotNull(root.get("trackId")))
            .groupBySeveral((from, cb) -> Lists.newArrayList(from.get("trackId"), from.get("fkSource"), from.get("fkSpotifyItemKind")))
            .orderBy((from, cb) -> cb.desc(cb.count(from.get("pk"))));
        SelectQueryBuilder<PlaybackHistory> trackChartsQueryGuild = trackChartsQuery.fork().where((cb, root) -> cb.equal(root.get("guildId"), guild.getId()));
        SelectQueryBuilder<PlaybackHistory> trackChartsQueryMonthly = trackChartsQuery.fork().where((cb, root) -> cb.greaterThan(root.get("timestamp"), startOfMonth));
        SelectQueryBuilder<PlaybackHistory> trackChartsQueryMonthlyGuild = trackChartsQueryMonthly.fork().where((cb, root) -> cb.equal(root.get("guildId"), guild.getId()));

        Query<Object[]> globalQuery = trackChartsQuery.build(session).setMaxResults(5);
        Query<Object[]> guildQuery = trackChartsQueryGuild.build(session).setMaxResults(5);

        Query<Object[]> globalQueryMonthly = trackChartsQueryMonthly.build(session).setMaxResults(5);
        Query<Object[]> guildQueryMonthly = trackChartsQueryMonthlyGuild.build(session).setMaxResults(5);

        List<Object[]> globalResults = globalQuery.getResultList();
        List<Object[]> guildResults = guildQuery.getResultList();
        List<Object[]> globalMonthlyResults = globalQueryMonthly.getResultList();
        List<Object[]> guildMonthlyResults = guildQueryMonthly.getResultList();

        @SuppressWarnings("unchecked")
        Query<Object[]> globalArtistQuery = session.createSQLQuery("select artist_pk, count(*) as c " +
            "from playback_history_artist group by artist_pk order by c desc limit 3");
        @SuppressWarnings("unchecked")
        Query<Object[]> guildArtistQuery = session.createSQLQuery("select artist_pk, count(*) as c from " +
            "playback_history_artist as p where p.playback_history_pk in(select pk from playback_history where guild_id = ?) " +
            "group by artist_pk order by c desc limit 3");
        guildArtistQuery.setParameter(1, guild.getId());

        @SuppressWarnings("unchecked")
        Query<Object[]> globalArtistMonthlyQuery = session.createSQLQuery("select artist_pk, count(*) as c " +
            "from playback_history_artist as p " +
            "where p.playback_history_pk in(select pk from playback_history where timestamp > ?) " +
            "group by artist_pk order by c desc limit 3");
        globalArtistMonthlyQuery.setParameter(1, dateAtStartOfMonth);

        @SuppressWarnings("unchecked")
        Query<Object[]> guildArtistMonthlyQuery = session.createSQLQuery("select artist_pk, count(*) as c " +
            "from playback_history_artist where playback_history_pk in(select pk from playback_history " +
            "where timestamp > ? and guild_id = ?) " +
            "group by artist_pk order by c desc limit 3");
        guildArtistMonthlyQuery.setParameter(1, dateAtStartOfMonth);
        guildArtistMonthlyQuery.setParameter(2, guild.getId());

        List<Object[]> globalArtists = globalArtistQuery.getResultList();
        List<Object[]> guildArtists = guildArtistQuery.getResultList();
        List<Object[]> globalArtistsMonthly = globalArtistMonthlyQuery.getResultList();
        List<Object[]> guildArtistMonthly = guildArtistMonthlyQuery.getResultList();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Global", "Shows the charts across all guilds", false);
        addTrackCharts(globalResults, embedBuilder, "All time", session);
        addArtists(globalArtists, embedBuilder, "All time");
        embedBuilder.addBlankField(true);
        addTrackCharts(globalMonthlyResults, embedBuilder, "Monthly", session);
        addArtists(globalArtistsMonthly, embedBuilder, "Monthly");
        embedBuilder.addBlankField(false);
        embedBuilder.addField("Guild", "Shows the charts for this guild", false);
        addTrackCharts(guildResults, embedBuilder, "All time", session);
        addArtists(guildArtists, embedBuilder, "All time");
        embedBuilder.addBlankField(true);
        addTrackCharts(guildMonthlyResults, embedBuilder, "Monthly", session);
        addArtists(guildArtistMonthly, embedBuilder, "Monthly");
        sendMessage(embedBuilder);
    }

    private void addTrackCharts(List<Object[]> queryResults, EmbedBuilder embedBuilder, String period, Session session) {
        Map<Playable, Long> tracksWithPlayedAmount = new HashMap<>();
        List<Playable> tracks = Lists.newArrayList();
        for (Object[] record : queryResults) {
            long playedAmount = (Long) record[2];
            try {
                Playable track = getTrackForRecord(record, session);
                if (track != null) {
                    tracksWithPlayedAmount.put(track, playedAmount);
                    tracks.add(track);
                }
            } catch (UnsupportedOperationException e) {
                throw e;
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).error(String.format("Error loading charts item from source %s: %s", record[0], record[1]), e);
            }
        }

        String title = period + " - Track Charts";
        if (!tracks.isEmpty()) {
            Util.appendEmbedList(
                embedBuilder,
                tracks,
                track -> tracksWithPlayedAmount.get(track) + " - " + track.display(),
                title,
                true
            );
        } else {
            embedBuilder.addField(title, "No data", true);
        }
    }

    private void addArtists(List<Object[]> records, EmbedBuilder embedBuilder, String period) {
        Session session = getContext().getSession();
        Map<Artist, BigInteger> artistsWithPlayedAmount = new HashMap<>();
        List<Artist> artists = Lists.newArrayList();
        for (Object[] record : records) {
            BigInteger artistPk = (BigInteger) record[0];
            BigInteger playedCount = (BigInteger) record[1];
            Artist artist = session.load(Artist.class, artistPk.longValue());
            artists.add(artist);
            artistsWithPlayedAmount.put(artist, playedCount);
        }

        String title = period + " - Artist Charts";
        if (!artists.isEmpty()) {
            Util.appendEmbedList(
                embedBuilder,
                artists,
                artist -> artistsWithPlayedAmount.get(artist) + " - " + artist.getName(),
                title,
                true
            );
        } else {
            embedBuilder.addField(title, "No data", true);
        }
    }

    private Playable getTrackForRecord(Object[] record, Session session) throws Exception {
        long sourceEntityPk = (Long) record[0];
        PlaybackHistorySource sourceEntity = session.load(PlaybackHistorySource.class, sourceEntityPk);
        Playable.Source source = sourceEntity.asEnum();
        String id = (String) record[1];
        Long spotifyItemKindPk = (Long) record[3];
        SpotifyItemKind spotifyItemKind = spotifyItemKindPk != null ? session.load(SpotifyItemKind.class, spotifyItemKindPk) : null;
        switch (source) {
            case SPOTIFY:
                return runWithCredentials(() -> {
                    if (spotifyItemKind == null) {
                        throw new IllegalStateException("spotifyItemKind cannot be null for PlaybackHistory entries of source SPOTIFY");
                    }

                    SpotifyTrackKind kind = spotifyItemKind.asEnum();
                    SpotifyService spotifyService = getSpotifyService();
                    SpotifyTrack track = kind.loadSingleItem(spotifyService, id);

                    if (track == null) {
                        return null;
                    }

                    return new PlayableTrackWrapper(track);
                });
            case YOUTUBE:
                YouTubeService youTubeService = Botify.get().getAudioManager().getYouTubeService();
                try {
                    return youTubeService.getVideoForId(id);
                } catch (FriendlyException e) {
                    return null;
                }
            case URL:
                return playableFactory.createPlayable(id, getContext().getSpotifyApi(), false);
        }

        throw new UnsupportedOperationException("Unsupported source " + sourceEntity);
    }

    @Override
    public void onSuccess() {
    }

}
