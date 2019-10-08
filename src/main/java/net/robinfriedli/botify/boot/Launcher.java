package net.robinfriedli.botify.boot;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.cron.CronJobService;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.entities.xml.CronJobContribution;
import net.robinfriedli.botify.entities.xml.EmbedDocumentContribution;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.entities.xml.HttpHandlerContribution;
import net.robinfriedli.botify.entities.xml.StartupTaskContribution;
import net.robinfriedli.botify.entities.xml.WidgetContribution;
import net.robinfriedli.botify.listeners.CommandListener;
import net.robinfriedli.botify.listeners.GuildJoinListener;
import net.robinfriedli.botify.listeners.VoiceChannelListener;
import net.robinfriedli.botify.listeners.WidgetListener;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.botify.servers.HttpServerStarter;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.JxpBuilder;
import net.robinfriedli.jxp.persist.Context;
import org.discordbots.api.client.DiscordBotListAPI;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

public class Launcher {

    public static void main(String[] args) {
        // setup logger
        System.setProperty("log4j.configurationFile", "./resources/log4j2.properties");
        // setup ehcache configuration
        System.setProperty("net.sf.ehcache.configurationResourceName", "ehcache.xml");
        Logger logger = LoggerFactory.getLogger(Launcher.class);
        logger.info("Using java version " + System.getProperty("java.runtime.version"));
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
            String hibernateConfigurationPath = PropertiesLoadingService.requireProperty("HIBERNATE_CONFIGURATION");
            String discordBotId = PropertiesLoadingService.loadProperty("DISCORD_BOT_ID");
            String discordbotsToken = PropertiesLoadingService.loadProperty("DISCORDBOTS_TOKEN");
            // setup persistence
            JxpBackend jxpBackend = new JxpBuilder()
                .mapClass("command", CommandContribution.class)
                .mapClass("commandInterceptor", CommandInterceptorContribution.class)
                .mapClass("httpHandler", HttpHandlerContribution.class)
                .mapClass("embedDocument", EmbedDocumentContribution.class)
                .mapClass("startupTask", StartupTaskContribution.class)
                .mapClass("guildProperty", GuildPropertyContribution.class)
                .mapClass("cronJob", CronJobContribution.class)
                .mapClass("widget", WidgetContribution.class)
                .mapClass("widgetAction", WidgetContribution.WidgetActionContribution.class)
                .build();

            StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().configure(new File(hibernateConfigurationPath)).build();
            MetadataSources metadataSources = new MetadataSources(serviceRegistry);
            SessionFactory sessionFactory = metadataSources.getMetadataBuilder().build().buildSessionFactory();
            StaticSessionProvider.sessionFactory = sessionFactory;

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

            // setup JDA
            JDA jda = new JDABuilder(AccountType.BOT)
                .setToken(discordToken)
                .setStatus(OnlineStatus.IDLE)
                .build()
                .awaitReady();

            // setup discordbots.org
            DiscordBotListAPI discordBotListAPI;
            if (!Strings.isNullOrEmpty(discordBotId) && !Strings.isNullOrEmpty(discordbotsToken)) {
                discordBotListAPI = new DiscordBotListAPI.Builder()
                    .botId(discordBotId)
                    .token(discordbotsToken)
                    .build();
                discordBotListAPI.setStats(jda.getGuilds().size());
            } else {
                logger.warn("discordbots.org api not set up, missing properties");
                discordBotListAPI = null;
            }

            // setup botify
            LoginManager loginManager = new LoginManager();
            GuildManager.Mode mode = modePartitioned ? GuildManager.Mode.PARTITIONED : GuildManager.Mode.SHARED;
            MessageService messageService = new MessageService();

            Context commandContributionContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("COMMANDS_PATH"));
            Context commandInterceptorContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("COMMAND_INTERCEPTORS_PATH"));
            Context guildPropertyContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("GUILD_PROPERTIES_PATH"));

            CommandManager commandManager = new CommandManager(commandContributionContext, commandInterceptorContext);
            GuildPropertyManager guildPropertyManager = new GuildPropertyManager(guildPropertyContext);
            GuildManager guildManager = new GuildManager(jxpBackend, mode);
            AudioManager audioManager = new AudioManager(youTubeService, sessionFactory, guildManager);
            CommandExecutionQueueManager executionQueueManager = new CommandExecutionQueueManager();
            SecurityManager securityManager = new SecurityManager(guildManager);

            CommandListener commandListener = new CommandListener(executionQueueManager, commandManager, guildManager, messageService, sessionFactory, spotifyApiBuilder);
            GuildJoinListener guildJoinListener = new GuildJoinListener(executionQueueManager, discordBotListAPI, guildManager);
            WidgetListener widgetListener = new WidgetListener(guildManager, messageService);
            VoiceChannelListener voiceChannelListener = new VoiceChannelListener(audioManager);

            Botify botify = new Botify(audioManager,
                executionQueueManager,
                commandManager,
                guildManager,
                guildPropertyManager,
                jda,
                jxpBackend,
                loginManager,
                messageService,
                securityManager,
                sessionFactory,
                spotifyApiBuilder,
                commandListener, guildJoinListener, widgetListener, voiceChannelListener);
            commandManager.initializeInterceptorChain();

            // start servers
            Context httpHanldersContext = jxpBackend.getContext(httpHandlersPath);
            HttpServerStarter serverStarter = new HttpServerStarter(httpHanldersContext);
            serverStarter.start();

            // setup guilds
            StaticSessionProvider.invokeWithSession(session -> {
                // setup current thread session and handle all guilds within one session instead of opening a new session for each
                for (Guild guild : jda.getGuilds()) {
                    guildManager.addGuild(guild);
                    executionQueueManager.addGuild(guild);
                }
            });

            // run startup tasks
            Context context = jxpBackend.getContext(startupTasksPath);
            for (StartupTaskContribution element : context.getInstancesOf(StartupTaskContribution.class)) {
                element.instantiate().perform();
            }

            CronJobService cronJobService = new CronJobService(jxpBackend.getContext(PropertiesLoadingService.requireProperty("CRON_JOBS_PATH")));
            cronJobService.scheduleAll();

            Botify.registerListeners();
            jda.getPresence().setStatus(OnlineStatus.ONLINE);
            logger.info("All starters done");
        } catch (Throwable e) {
            logger.error("Exception in starter. Application will terminate.", e);
            System.exit(1);
        }
    }

}
