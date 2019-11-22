package net.robinfriedli.botify.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
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
import net.robinfriedli.botify.entities.xml.Version;
import net.robinfriedli.botify.entities.xml.WidgetContribution;
import net.robinfriedli.botify.function.CheckedConsumer;
import net.robinfriedli.botify.listeners.CommandListener;
import net.robinfriedli.botify.listeners.GuildManagementListener;
import net.robinfriedli.botify.listeners.VoiceChannelListener;
import net.robinfriedli.botify.listeners.WidgetListener;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.botify.servers.HttpServerStarter;
import net.robinfriedli.botify.util.StaticSessionProvider;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.JxpBuilder;
import net.robinfriedli.jxp.persist.Context;
import org.discordbots.api.client.DiscordBotListAPI;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import static net.robinfriedli.botify.util.PropertiesLoadingService.*;

public class Launcher {

    public static void main(String[] args) {
        // setup logger
        System.setProperty("log4j.configurationFile", "log4j2.properties");
        // setup ehcache configuration
        System.setProperty("net.sf.ehcache.configurationResourceName", "ehcache.xml");
        Logger logger = LoggerFactory.getLogger(Launcher.class);
        logger.info("Using java version " + System.getProperty("java.runtime.version"));
        try {
            // initialize property values
            String redirectUri = requireProperty("REDIRECT_URI");
            String discordToken = requireProperty("DISCORD_TOKEN");
            String clientId = requireProperty("SPOTIFY_CLIENT_ID");
            String clientSecret = requireProperty("SPOTIFY_CLIENT_SECRET");
            String youTubeCredentials = requireProperty("YOUTUBE_CREDENTIALS");
            boolean modePartitioned = loadBoolProperty("MODE_PARTITIONED");
            String hibernateConfigurationResource = requireProperty("HIBERNATE_CONFIGURATION");
            String discordBotId = loadProperty("DISCORD_BOT_ID");
            String discordbotsToken = loadProperty("DISCORDBOTS_TOKEN");
            int youtubeApiDailyQuota = requireProperty(Integer.class, "YOUTUBE_API_DAILY_QUOTA");
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
                .mapClass("version", Version.class)
                .mapClass("feature", Version.Feature.class)
                .build();

            StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().configure(hibernateConfigurationResource).build();
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
            YouTubeService youTubeService = new YouTubeService(youTube, youTubeCredentials, youtubeApiDailyQuota);

            // setup JDA
            ShardManager shardManager = new DefaultShardManagerBuilder()
                .setToken(discordToken)
                .setStatus(OnlineStatus.IDLE)
                .build();

            shardManager.getShards().forEach((CheckedConsumer<JDA>) JDA::awaitReady);

            // setup discordbots.org
            DiscordBotListAPI discordBotListAPI;
            if (!Strings.isNullOrEmpty(discordBotId) && !Strings.isNullOrEmpty(discordbotsToken)) {
                discordBotListAPI = new DiscordBotListAPI.Builder()
                    .botId(discordBotId)
                    .token(discordbotsToken)
                    .build();
                for (JDA shard : shardManager.getShards()) {
                    JDA.ShardInfo shardInfo = shard.getShardInfo();
                    discordBotListAPI.setStats(shardInfo.getShardId(), shardInfo.getShardTotal(), shard.getGuilds().size());
                }
            } else {
                logger.warn("discordbots.org api not set up, missing properties");
                discordBotListAPI = null;
            }

            // setup botify
            LoginManager loginManager = new LoginManager();
            GuildManager.Mode mode = modePartitioned ? GuildManager.Mode.PARTITIONED : GuildManager.Mode.SHARED;
            MessageService messageService = new MessageService();

            Context commandContributionContext = jxpBackend.getContext(requireContributionFile("commands.xml"));
            Context commandInterceptorContext = jxpBackend.getContext(requireContributionFile("commandInterceptors.xml"));
            Context guildPropertyContext = jxpBackend.getContext(requireContributionFile("guildProperties.xml"));

            CommandManager commandManager = new CommandManager(commandContributionContext, commandInterceptorContext);
            GuildPropertyManager guildPropertyManager = new GuildPropertyManager(guildPropertyContext);
            GuildManager guildManager = new GuildManager(jxpBackend, mode);
            AudioManager audioManager = new AudioManager(youTubeService, sessionFactory, guildManager);
            CommandExecutionQueueManager executionQueueManager = new CommandExecutionQueueManager();
            SecurityManager securityManager = new SecurityManager(guildManager);

            CommandListener commandListener = new CommandListener(executionQueueManager, commandManager, guildManager, messageService, sessionFactory, spotifyApiBuilder);
            GuildManagementListener guildManagementListener = new GuildManagementListener(executionQueueManager, discordBotListAPI, guildManager, shardManager);
            WidgetListener widgetListener = new WidgetListener(guildManager, messageService);
            VoiceChannelListener voiceChannelListener = new VoiceChannelListener(audioManager);
            VersionManager versionManager = new VersionManager(jxpBackend.getContext(requireResourceFile("versions.xml")));

            Context httpHanldersContext = jxpBackend.getContext(requireContributionFile("httpHandlers.xml"));
            HttpServerStarter serverStarter = new HttpServerStarter(httpHanldersContext);
            CronJobService cronJobService = new CronJobService(jxpBackend.getContext(requireContributionFile("cronJobs.xml")));

            Botify botify = new Botify(audioManager,
                executionQueueManager,
                commandManager,
                cronJobService,
                guildManager,
                guildPropertyManager,
                jxpBackend,
                loginManager,
                messageService,
                securityManager,
                sessionFactory,
                shardManager,
                spotifyApiBuilder,
                versionManager,
                commandListener, guildManagementListener, widgetListener, voiceChannelListener);

            commandManager.initializeInterceptorChain();
            serverStarter.start();

            // setup guilds
            StaticSessionProvider.invokeWithSession(session -> {
                // setup current thread session and handle all guilds within one session instead of opening a new session for each
                for (Guild guild : shardManager.getGuilds()) {
                    guildManager.addGuild(guild);
                    executionQueueManager.addGuild(guild);
                }
            });

            // run startup tasks
            Context context = jxpBackend.getContext(requireContributionFile("startupTasks.xml"));
            for (StartupTaskContribution element : context.getInstancesOf(StartupTaskContribution.class)) {
                element.instantiate().perform();
            }

            cronJobService.scheduleAll();

            Botify.registerListeners();
            shardManager.setStatus(OnlineStatus.ONLINE);
            logger.info("All starters done");
        } catch (Throwable e) {
            logger.error("Exception in starter. Application will terminate.", e);
            System.exit(1);
        }
    }

}
