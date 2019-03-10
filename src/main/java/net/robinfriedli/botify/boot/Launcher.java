package net.robinfriedli.botify.boot;

import java.io.File;

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
import net.robinfriedli.botify.discord.DiscordListener;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.Artist;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.entities.CommandInterceptorContribution;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.HttpHandlerContribution;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.listener.InterceptorChain;
import net.robinfriedli.botify.listener.PlaylistItemTimestampListener;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.botify.servers.HttpServerStarter;
import net.robinfriedli.botify.util.ParameterContainer;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.JxpBuilder;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;

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
            String httpHandlersPath = PropertiesLoadingService.requireProperty("HTTP_HANDLERS_PATH");
            // setup persistence
            JxpBackend jxpBackend = new JxpBuilder()
                .mapClass("guildSpecification", GuildSpecification.class)
                .mapClass("command", CommandContribution.class)
                .mapClass("accessConfiguration", AccessConfiguration.class)
                .mapClass("commandInterceptor", CommandInterceptorContribution.class)
                .mapClass("httpHandler", HttpHandlerContribution.class)
                .build();

            Configuration configuration = new Configuration();
            configuration.configure(new File("./resources/hibernate.cfg.xml"));
            configuration.addAnnotatedClass(Playlist.class);
            configuration.addAnnotatedClass(Song.class);
            configuration.addAnnotatedClass(Video.class);
            configuration.addAnnotatedClass(UrlTrack.class);
            configuration.addAnnotatedClass(Artist.class);
            configuration.addAnnotatedClass(PlaylistItem.class);
            configuration.addAnnotatedClass(CommandHistory.class);
            configuration.addAnnotatedClass(PlaybackHistory.class);
            StandardServiceRegistryBuilder serviceBuilder = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties());
            SessionFactory sessionFactory = configuration.buildSessionFactory(serviceBuilder.build());

            // setup spotify api
            SpotifyApi.Builder spotifyApiBuilder = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(SpotifyHttpManager.makeUri(redirectUri));
            // setup YouTube API
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JacksonFactory jacksonFactory = JacksonFactory.getDefaultInstance();
            YouTube youTube = new YouTube.Builder(httpTransport, jacksonFactory, httpRequest -> {
                // no-op
            }).setApplicationName("botify-youtube-search").build();
            YouTubeService youTubeService = new YouTubeService(youTube, youTubeCredentials);
            // setup JDA and DiscordListener
            LoginManager loginManager = new LoginManager();
            DiscordListener discordListener = new DiscordListener(jxpBackend, spotifyApiBuilder, sessionFactory, loginManager, youTubeService, logger);
            JDA jda = new JDABuilder(AccountType.BOT)
                .setToken(discordToken)
                .build()
                .awaitReady();

            // start servers
            Context httpHanldersContext = jxpBackend.getContext(httpHandlersPath);
            ParameterContainer parameterContainer = new ParameterContainer(jda, spotifyApiBuilder.build(), loginManager, sessionFactory, discordListener, discordListener.getAudioManager());
            HttpServerStarter serverStarter = new HttpServerStarter(httpHanldersContext, parameterContainer);
            serverStarter.start();

            // run startup tasks
            Context context = jxpBackend.getContext(startupTasksPath);
            Session session = sessionFactory.withOptions().interceptor(InterceptorChain.of(PlaylistItemTimestampListener.class)).openSession();
            for (XmlElement element : context.getElements()) {
                Class<StartupTask> task = (Class<StartupTask>) Class.forName(element.getAttribute("implementation").getValue());
                task.getConstructor().newInstance().perform(jxpBackend, jda, spotifyApiBuilder.build(), youTubeService, session);
            }
            session.close();

            if (modePartitioned) {
                discordListener.setupGuilds(DiscordListener.Mode.PARTITIONED, jda.getGuilds());
            } else {
                discordListener.setupGuilds(DiscordListener.Mode.SHARED, jda.getGuilds());
            }

            jda.addEventListener(discordListener);
            logger.info("All starters done");
        } catch (Throwable e) {
            logger.error("Exception in starter. Application will terminate.", e);
            System.exit(1);
        }
    }

}
