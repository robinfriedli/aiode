package net.robinfriedli.botify.boot;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.common.base.Strings;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
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
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import net.robinfriedli.botify.listeners.CommandListener;
import net.robinfriedli.botify.listeners.GuildManagementListener;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.dv8tion.jda.api.requests.GatewayIntent.*;

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
            int youtubeApiDailyQuota = PropertiesLoadingService.requireProperty(Integer.class, "YOUTUBE_API_DAILY_QUOTA");
            String nativeAudioBuffer = PropertiesLoadingService.loadProperty("NATIVE_AUDIO_BUFFER");
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
            YouTubeService youTubeService = new YouTubeService(youTube, youTubeCredentials, youtubeApiDailyQuota);

            // setup top.gg api
            DiscordBotListAPI discordBotListAPI;
            if (!Strings.isNullOrEmpty(discordBotId) && !Strings.isNullOrEmpty(discordbotsToken)) {
                discordBotListAPI = new DiscordBotListAPI.Builder()
                    .botId(discordBotId)
                    .token(discordbotsToken)
                    .build();
            } else {
                logger.warn("discordbots.org api not set up, missing properties");
                discordBotListAPI = null;
            }

            // prepare startup tasks
            Context startupTaskContext = jxpBackend.getContext(startupTasksPath);
            List<StartupTaskContribution> startupTaskContributions = startupTaskContext.getInstancesOf(StartupTaskContribution.class);
            StartupListener startupListener = new StartupListener(discordBotListAPI, startupTaskContributions);

            // setup JDA
            EnumSet<GatewayIntent> gatewayIntents = EnumSet.of(GUILD_MESSAGES, GUILD_MESSAGE_REACTIONS, GUILD_VOICE_STATES);
            DefaultShardManagerBuilder shardManagerBuilder = DefaultShardManagerBuilder.create(discordToken, gatewayIntents)
                .disableCache(EnumSet.of(
                    CacheFlag.ACTIVITY,
                    CacheFlag.CLIENT_STATUS,
                    CacheFlag.EMOTE,
                    CacheFlag.MEMBER_OVERRIDES,
                    CacheFlag.ONLINE_STATUS,
                    CacheFlag.ROLE_TAGS
                ))
                .setMemberCachePolicy(MemberCachePolicy.DEFAULT)
                .setStatus(OnlineStatus.IDLE)
                .setChunkingFilter(ChunkingFilter.NONE)
                .addEventListeners(startupListener);

            int nativeAudioBufferMs;
            if (!Strings.isNullOrEmpty(nativeAudioBuffer) && (nativeAudioBufferMs = Integer.parseInt(nativeAudioBuffer)) > 0) {
                if (platformSupportsJdaNas()) {
                    logger.info("Using NativeAudioSendFactory with a buffer duration of " + nativeAudioBufferMs + "ms");
                    shardManagerBuilder = shardManagerBuilder.setAudioSendFactory(new NativeAudioSendFactory(nativeAudioBufferMs));
                } else {
                    logger.info("NativeAudioSendFactory not supported by current platform");
                }
            } else {
                logger.info("Native audio buffer disabled");
            }

            ShardManager shardManager = shardManagerBuilder.build();

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
            GuildManagementListener guildManagementListener = new GuildManagementListener(executionQueueManager, discordBotListAPI, guildManager);
            WidgetListener widgetListener = new WidgetListener(guildManager, messageService);
            VoiceChannelListener voiceChannelListener = new VoiceChannelListener(audioManager);
            VersionManager versionManager = new VersionManager(jxpBackend.getContext(PropertiesLoadingService.requireProperty("VERSIONS_PATH")));

            Botify botify = new Botify(audioManager,
                executionQueueManager,
                commandManager,
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

            // start servers
            Context httpHanldersContext = jxpBackend.getContext(httpHandlersPath);
            HttpServerStarter serverStarter = new HttpServerStarter(httpHanldersContext);
            serverStarter.start();

            // run startup tasks
            for (StartupTaskContribution element : startupTaskContributions) {
                if (!element.getAttribute("runForEachShard").getBool()) {
                    element.instantiate().runTask(null);
                }
            }
            startupListener.setBotify(botify);

            CronJobService cronJobService = new CronJobService(jxpBackend.getContext(PropertiesLoadingService.requireProperty("CRON_JOBS_PATH")));
            cronJobService.scheduleAll();

            Botify.registerListeners();
            shardManager.setStatus(OnlineStatus.ONLINE);
            logger.info("All starters done");
        } catch (Throwable e) {
            logger.error("Exception in starter. Application will terminate.", e);
            System.exit(1);
        }
    }

    private static boolean platformSupportsJdaNas() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        return (osName.contains("linux") || osName.contains("windows"))
            && (osArch.contains("amd64") || osArch.contains("x86"));
    }

    private static class StartupListener extends ListenerAdapter {

        private static final ExecutorService STARTUP_TASK_EXECUTOR = new ThreadPoolExecutor(
            0, 1,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("startup-task-thread");
                thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
                return thread;
            }
        );

        private final CompletableFuture<Botify> futureBotify;
        @Nullable
        private final DiscordBotListAPI discordBotListAPI;
        private final List<StartupTaskContribution> startupTaskContributions;

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private StartupListener(@Nullable DiscordBotListAPI discordBotListAPI, List<StartupTaskContribution> startupTaskContributions) {
            this.discordBotListAPI = discordBotListAPI;
            futureBotify = new CompletableFuture<>();
            this.startupTaskContributions = startupTaskContributions;
        }

        @Override
        public void onReady(@NotNull ReadyEvent event) {
            STARTUP_TASK_EXECUTOR.execute(() -> {
                JDA jda = event.getJDA();

                if (discordBotListAPI != null) {
                    try {
                        JDA.ShardInfo shardInfo = jda.getShardInfo();
                        discordBotListAPI.setStats(shardInfo.getShardId(), shardInfo.getShardTotal(), (int) jda.getGuildCache().size());
                    } catch (Exception e) {
                        logger.error("Exception setting discordBotListAPI stats", e);
                    }
                }

                Botify botify;
                try {
                    botify = futureBotify.get(5, TimeUnit.MINUTES);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    throw new RuntimeException("Exception awaiting dependency for StartupListener", e);
                }

                CommandExecutionQueueManager executionQueueManager = botify.getExecutionQueueManager();
                GuildManager guildManager = botify.getGuildManager();

                StaticSessionProvider.consumeSession(session -> {
                    // setup current thread session and handle all guilds within one session instead of opening a new session for each
                    for (Guild guild : jda.getGuilds()) {
                        executionQueueManager.addGuild(guild);
                        guildManager.addGuild(guild);
                    }
                });

                for (StartupTaskContribution element : startupTaskContributions) {
                    if (element.getAttribute("runForEachShard").getBool()) {
                        try {
                            element.instantiate().runTask(jda);
                        } catch (Exception e) {
                            String msg = String.format(
                                "Startup task %s has thrown an exception for shard %s",
                                element.getAttribute("implementation").getValue(),
                                jda
                            );
                            logger.error(msg, e);
                        }
                    }
                }
            });
        }

        public void setBotify(Botify botify) {
            futureBotify.complete(botify);
        }

    }

}
