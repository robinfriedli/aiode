package net.robinfriedli.botify.command.commands;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.wrapper.spotify.model_objects.specification.Track;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.audio.PlayableFactory;
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.audio.spotify.TrackWrapper;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Artist;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.util.Util;
import org.hibernate.Session;
import org.hibernate.query.Query;

public class ChartsCommand extends AbstractCommand {

    public ChartsCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() throws Exception {
        Session session = getContext().getSession();
        Guild guild = getContext().getGuild();

        Query<Object[]> globalQuery = session.createQuery("select source, trackId, count(*) as c from " + PlaybackHistory.class.getName()
            + " where trackId is not null group by trackId, source order by c desc", Object[].class)
            .setMaxResults(5);
        Query<Object[]> guildQuery = session.createQuery("select source, trackId, count(*) as c from " + PlaybackHistory.class.getName()
            + " where trackId is not null and guildId = '" + guild.getId() + "' group by trackId, source order by c desc", Object[].class)
            .setMaxResults(5);
        List<Object[]> globalResults = globalQuery.getResultList();
        List<Object[]> guildResults = guildQuery.getResultList();

        @SuppressWarnings("unchecked")
        Query<Object[]> globalArtistQuery = session.createSQLQuery("select artists_pk, count(*) as c " +
            "from playback_history_artist group by artists_pk order by c desc limit 3");
        @SuppressWarnings("unchecked")
        Query<Object[]> guildArtistQuery = session.createSQLQuery("select artists_pk, count(*) as c from " +
            "(select * from playback_history_artist where playbackhistory_pk in(select pk from playback_history where guild_id = '484071462622330881')) as foo " +
            "group by artists_pk order by c desc limit 3");
        List<Object[]> globalArtists = globalArtistQuery.getResultList();
        List<Object[]> guildArtists = guildArtistQuery.getResultList();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Global", "Shows the charts across all guilds", false);
        addTrackCharts(globalResults, embedBuilder);
        addArtists(globalArtists, embedBuilder);
        embedBuilder.addBlankField(false);
        embedBuilder.addField("Guild", "Shows the charts for this guild", false);
        addTrackCharts(guildResults, embedBuilder);
        addArtists(guildArtists, embedBuilder);
        sendMessage(embedBuilder);
    }

    private void addTrackCharts(List<Object[]> queryResults, EmbedBuilder embedBuilder) throws Exception {
        Map<Playable, Long> tracksWithPlayedAmount = new HashMap<>();
        List<Playable> tracks = Lists.newArrayList();
        for (Object[] record : queryResults) {
            long playedAmount = (Long) record[2];
            Playable track = getTrackForRecord(record);
            tracksWithPlayedAmount.put(track, playedAmount);
            tracks.add(track);
        }

        String title = "Count - Track";
        if (!tracks.isEmpty()) {
            Util.appendEmbedList(
                embedBuilder,
                tracks,
                track -> tracksWithPlayedAmount.get(track) + " - " + track.getDisplayInterruptible(),
                title
            );
        } else {
            embedBuilder.addField(title, "No data", false);
        }
    }

    private void addArtists(List<Object[]> records, EmbedBuilder embedBuilder) {
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

        String title = "Count - Artist";
        if (!artists.isEmpty()) {
            Util.appendEmbedList(
                embedBuilder,
                artists,
                artist -> artistsWithPlayedAmount.get(artist) + " - " + artist.getName(),
                title
            );
        } else {
            embedBuilder.addField(title, "No data", false);
        }
    }

    private Playable getTrackForRecord(Object[] record) throws Exception {
        String source = (String) record[0];
        String id = (String) record[1];
        switch (source) {
            case "Spotify":
                return runWithCredentials(() -> {
                    SpotifyService spotifyService = getSpotifyService();
                    Track track = spotifyService.getTrack(id);
                    return new TrackWrapper(track);
                });
            case "YouTube":
                YouTubeService youTubeService = Botify.get().getAudioManager().getYouTubeService();
                return youTubeService.videoForId(id);
            case "Url":
                AudioManager audioManager = Botify.get().getAudioManager();
                PlayableFactory playableFactory = audioManager.createPlayableFactory(getContext().getGuild());
                return playableFactory.createPlayable(id, getContext().getSpotifyApi(), false);
        }

        throw new UnsupportedOperationException("Unsupported source " + source);
    }

    @Override
    public void onSuccess() {
    }

}
