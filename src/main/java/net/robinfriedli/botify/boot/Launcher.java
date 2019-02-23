package net.robinfriedli.botify.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.discord.AlertEventListener;
import net.robinfriedli.botify.discord.DiscordListener;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.botify.login.LoginServer;
import net.robinfriedli.botify.util.PlaylistListener;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.JxpBuilder;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

public class Launcher {

    public static void main(String[] args) {
        // setup logger
        System.setProperty("log4j.configurationFile", "./resources/log4j2.properties");
        Logger logger = LoggerFactory.getLogger(Launcher.class);
        try {
            // initialize property values
            String redirectUri = PropertiesLoadingService.requireProperty("REDIRECT_URI");
            String discordToken = PropertiesLoadingService.requireProperty("DISCORD_TOKEN");
            String clientId = PropertiesLoadingService.requireProperty("SPOTIFY_CLIENT_ID");
            String clientSecret = PropertiesLoadingService.requireProperty("SPOTIFY_CLIENT_SECRET");
            String youTubeCredentials = PropertiesLoadingService.requireProperty("YOUTUBE_CREDENTIALS");
            String startupTasksPath = PropertiesLoadingService.requireProperty("STARTUP_TASKS_PATH");
            boolean modePartitioned = PropertiesLoadingService.loadBoolProperty("MODE_PARTITIONED");
            // setup JXP
            JxpBackend jxpBackend = new JxpBuilder()
                .addListeners(new AlertEventListener(logger), new PlaylistListener())
                .mapClass("playlist", Playlist.class)
                .mapClass("song", Song.class)
                .mapClass("video", Video.class)
                .mapClass("urlTrack", UrlTrack.class)
                .mapClass("guildSpecification", GuildSpecification.class)
                .mapClass("command", CommandContribution.class)
                .mapClass("accessConfiguration", AccessConfiguration.class)
                .build();

            // setup spotify api
            SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(SpotifyHttpManager.makeUri(redirectUri))
                .build();
            // setup YouTube API
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JacksonFactory jacksonFactory = JacksonFactory.getDefaultInstance();
            YouTube youTube = new YouTube.Builder(httpTransport, jacksonFactory, httpRequest -> {
                // no-op
            }).setApplicationName("botify-youtube-search").build();
            YouTubeService youTubeService = new YouTubeService(youTube, youTubeCredentials);
            // setup JDA and DiscordListener
            LoginManager loginManager = new LoginManager();
            DiscordListener discordListener = new DiscordListener(spotifyApi, jxpBackend, loginManager, youTubeService, logger);
            JDA jda = new JDABuilder(AccountType.BOT)
                .setToken(discordToken)
                .addEventListener(discordListener)
                .build()
                .awaitReady();

            // run startup tasks
            Context context = jxpBackend.getContext(startupTasksPath);
            for (XmlElement element : context.getElements()) {
                Class<StartupTask> task = (Class<StartupTask>) Class.forName(element.getAttribute("implementation").getValue());
                task.getConstructor().newInstance().perform(jxpBackend, jda, spotifyApi, youTubeService);
            }

            if (modePartitioned) {
                discordListener.setupGuilds(DiscordListener.Mode.PARTITIONED, jda.getGuilds());
            } else {
                discordListener.setupGuilds(DiscordListener.Mode.SHARED, jda.getGuilds());
            }

            LoginServer.start(jda, spotifyApi, loginManager);
            logger.info("All starters done");
        } catch (Exception e) {
            logger.error("Exception in starter. Application will terminate.", e);
            System.exit(1);
        }
    }

}
