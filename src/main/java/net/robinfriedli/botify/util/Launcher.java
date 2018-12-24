package net.robinfriedli.botify.util;

import java.io.IOException;
import java.security.GeneralSecurityException;

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
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.botify.login.LoginServer;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.JxpBuilder;

public class Launcher {

    public static void main(String[] args) {
        try {
            // initialize property values
            String redirectUri = PropertiesLoadingService.requireProperty("REDIRECT_URI");
            String discordToken = PropertiesLoadingService.requireProperty("DISCORD_TOKEN");
            String clientId = PropertiesLoadingService.requireProperty("SPOTIFY_CLIENT_ID");
            String clientSecret = PropertiesLoadingService.requireProperty("SPOTIFY_CLIENT_SECRET");
            String youTubeCredentials = PropertiesLoadingService.requireProperty("YOUTUBE_CREDENTIALS");
            boolean modePartitioned = PropertiesLoadingService.loadBoolProperty("MODE_PARTITIONED");
            // setup spotify api
            SpotifyApi spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(SpotifyHttpManager.makeUri(redirectUri))
                .build();
            // setup JXP
            JxpBackend jxpBackend = new JxpBuilder()
                .addListeners(new AlertEventListener(), new PlaylistListener())
                .mapClass("playlist", Playlist.class)
                .mapClass("song", Song.class)
                .mapClass("video", Video.class)
                .mapClass("guildSpecification", GuildSpecification.class)
                .build();
            // setup YouTube API
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JacksonFactory jacksonFactory = JacksonFactory.getDefaultInstance();
            YouTube youTube = new YouTube.Builder(httpTransport, jacksonFactory, httpRequest -> {
                // no-op
            }).setApplicationName("botify-youtube-search").build();
            YouTubeService youTubeService = new YouTubeService(youTube, youTubeCredentials);
            // setup JDA and DiscordListener
            LoginManager loginManager = new LoginManager(spotifyApi);
            DiscordListener discordListener = new DiscordListener(spotifyApi, jxpBackend, loginManager, youTubeService);
            JDA jda = new JDABuilder(AccountType.BOT)
                .setToken(discordToken)
                .addEventListener(discordListener)
                .build()
                .awaitReady();

            if (modePartitioned) {
                discordListener.setupGuilds(DiscordListener.Mode.PARTITIONED, jda.getGuilds());
            } else {
                discordListener.setupGuilds(DiscordListener.Mode.SHARED, jda.getGuilds());
            }

            LoginServer.start(jda, spotifyApi, loginManager);
            System.out.println("All starters done");
        } catch (GeneralSecurityException | InterruptedException | IOException e) {
            System.err.println("Exception in starter:");
            e.printStackTrace();
            System.exit(1);
        }
    }

}
