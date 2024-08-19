package net.robinfriedli.aiode.command.commands.general;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.ChartService;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.exec.BlockingTrackLoadingExecutor;
import net.robinfriedli.aiode.audio.playables.PlayableFactory;
import net.robinfriedli.aiode.audio.spotify.PlayableTrackWrapper;
import net.robinfriedli.aiode.audio.spotify.SpotifyService;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrack;
import net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.Artist;
import net.robinfriedli.aiode.entities.PlaybackHistorySource;
import net.robinfriedli.aiode.entities.SpotifyItemKind;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.filebroker.FilebrokerPlayableWrapper;
import net.robinfriedli.aiode.util.Util;
import net.robinfriedli.filebroker.FilebrokerApi;
import org.hibernate.Session;

public class ChartsCommand extends AbstractCommand {

    private final ChartService chartService;
    private final PlayableFactory playableFactory;

    public ChartsCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
        chartService = Aiode.get().getChartService();
        playableFactory = Aiode.get().getAudioManager().createPlayableFactory(getSpotifyService(), new BlockingTrackLoadingExecutor(), false);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        Guild guild = getContext().getGuild();
        User user = getContext().getUser();

        EmbedBuilder embedBuilder = new EmbedBuilder();

        if (argumentSet("guild")) {
            List<Object[]> guildResults = chartService.getGuildTrackChart(guild, session);
            List<Object[]> guildMonthlyResults = chartService.getGuildTrackMonthlyChart(guild, session);
            List<Object[]> guildArtists = chartService.getGuildArtistChart(guild, session);
            List<Object[]> guildArtistMonthly = chartService.getGuildArtistMonthlyChart(guild, session);

            embedBuilder.addField("Guild", "Shows the charts for this guild", false);
            addTrackCharts(guildResults, embedBuilder, "All time", session);
            addArtists(guildArtists, embedBuilder, "All time");
            embedBuilder.addBlankField(true);
            addTrackCharts(guildMonthlyResults, embedBuilder, "Monthly", session);
            addArtists(guildArtistMonthly, embedBuilder, "Monthly");
        } else if (argumentSet("user")) {
            List<Object[]> userTrackChart = chartService.getUserTrackChart(user, session);
            List<Object[]> userTrackMonthlyChart = chartService.getUserTrackMonthlyChart(user, session);
            List<Object[]> userArtistChart = chartService.getUserArtistChart(user, session);
            List<Object[]> userArtistMonthlyChart = chartService.getUserArtistMonthlyChart(user, session);

            embedBuilder.addField("User", "Shows the charts for this user", false);
            addTrackCharts(userTrackChart, embedBuilder, "All time", session);
            addArtists(userArtistChart, embedBuilder, "All time");
            embedBuilder.addBlankField(true);
            addTrackCharts(userTrackMonthlyChart, embedBuilder, "Monthly", session);
            addArtists(userArtistMonthlyChart, embedBuilder, "Monthly");
        } else {
            List<Object[]> globalResults = chartService.getPersistentGlobalTrackChart(session);
            List<Object[]> globalMonthlyResults = chartService.getPersistentGlobalTrackMonthlyChart(session);
            List<Object[]> globalArtists = chartService.getPersistentGlobalArtistChart(session);
            List<Object[]> globalArtistsMonthly = chartService.getPersistentGlobalArtistMonthlyChart(session);

            embedBuilder.addField("Global", "Shows the charts across all guilds", false);
            addTrackCharts(globalResults, embedBuilder, "All time", session);
            addArtists(globalArtists, embedBuilder, "All time");
            embedBuilder.addBlankField(true);
            addTrackCharts(globalMonthlyResults, embedBuilder, "Monthly", session);
            addArtists(globalArtistsMonthly, embedBuilder, "Monthly");
        }

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
        Map<Artist, Long> artistsWithPlayedAmount = new HashMap<>();
        List<Artist> artists = Lists.newArrayList();
        for (Object[] record : records) {
            Object artistPk = record[0];
            Long playedCount = (Long) record[1];
            Artist artist = session.getReference(Artist.class, artistPk);
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
        PlaybackHistorySource sourceEntity = session.getReference(PlaybackHistorySource.class, sourceEntityPk);
        Playable.Source source = sourceEntity.asEnum();
        String id = (String) record[1];
        Long spotifyItemKindPk = (Long) record[3];
        SpotifyItemKind spotifyItemKind = spotifyItemKindPk != null ? session.getReference(SpotifyItemKind.class, spotifyItemKindPk) : null;
        switch (source) {
            case SPOTIFY -> {
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
            }
            case YOUTUBE -> {
                YouTubeService youTubeService = Aiode.get().getAudioManager().getYouTubeService();
                try {
                    return youTubeService.getVideoForId(id);
                } catch (FriendlyException e) {
                    return null;
                }
            }
            case URL -> {
                return playableFactory.createPlayableContainerForUrl(id).loadPlayable(playableFactory);
            }
            case FILEBROKER -> {
                FilebrokerApi filebrokerApi = Aiode.get().getFilebrokerApi();
                FilebrokerApi.PostDetailed postDetailed = filebrokerApi.getPostAsync(Long.parseLong(id), null, null).get(10, TimeUnit.SECONDS);
                return new FilebrokerPlayableWrapper(new FilebrokerApi.Post(postDetailed));
            }
        }

        throw new UnsupportedOperationException("Unsupported source " + sourceEntity);
    }

    @Override
    public void onSuccess() {
    }

}
