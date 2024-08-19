package net.robinfriedli.aiode;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Sets;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.ChartService;
import net.robinfriedli.aiode.audio.playables.PlayableContainerManager;
import net.robinfriedli.aiode.boot.Shutdownable;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.boot.VersionManager;
import net.robinfriedli.aiode.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.boot.configurations.SpotifyComponent;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.SecurityManager;
import net.robinfriedli.aiode.command.widget.WidgetManager;
import net.robinfriedli.aiode.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.aiode.cron.CronJobService;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.exceptions.handler.ExceptionHandlerRegistry;
import net.robinfriedli.aiode.login.LoginManager;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import net.robinfriedli.aiode.scripting.GroovyVariableManager;
import net.robinfriedli.aiode.servers.HttpServerManager;
import net.robinfriedli.filebroker.FilebrokerApi;
import net.robinfriedli.jxp.api.JxpBackend;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import se.michaelthelin.spotify.SpotifyApi;

/**
 * This class offers several methods to manage the bot, such as un- / registering discord listeners or shutting down
 * or restarting the bot and serves as a registry for all major components, enabling static access or access from
 * outside of spring components.
 */
@Component
public class Aiode {

    public static final Logger LOGGER = LoggerFactory.getLogger(Aiode.class);

    public static final Set<Shutdownable> SHUTDOWNABLES = Sets.newHashSet();
    private static volatile boolean shuttingDown;

    private static Aiode instance;

    private final boolean mainInstance;

    private final AudioManager audioManager;
    private final ChartService chartService;
    private final CommandExecutionQueueManager executionQueueManager;
    private final CommandManager commandManager;
    private final ConfigurableApplicationContext springBootContext;
    private final CronJobService cronJobService;
    private final ExceptionHandlerRegistry exceptionHandlerRegistry;
    private final FilebrokerApi filebrokerApi;
    private final GroovySandboxComponent groovySandboxComponent;
    private final GroovyVariableManager groovyVariableManager;
    private final GuildManager guildManager;
    private final GuildPropertyManager guildPropertyManager;
    private final HibernateComponent hibernateComponent;
    private final HttpServerManager httpServerManager;
    private final JxpBackend jxpBackend;
    private final ListenerAdapter[] registeredListeners;
    private final LoginManager loginManager;
    private final MessageService messageService;
    private final PlayableContainerManager playableContainerManager;
    private final QueryBuilderFactory queryBuilderFactory;
    private final SecurityManager securityManager;
    private final ShardManager shardManager;
    private final SpotifyApi.Builder spotifyApiBuilder;
    private final SpotifyComponent spotifyComponent;
    private final SpringPropertiesConfig springPropertiesConfig;
    private final VersionManager versionManager;
    private final WidgetManager widgetManager;

    public Aiode(
        @Value("${aiode.preferences.main_instance:true}") boolean mainInstance,
        AudioManager audioManager,
        ChartService chartService,
        CommandExecutionQueueManager executionQueueManager,
        CommandManager commandManager,
        ConfigurableApplicationContext springBootContext,
        CronJobService cronJobService,
        ExceptionHandlerRegistry exceptionHandlerRegistry,
        FilebrokerApi filebrokerApi,
        GroovySandboxComponent groovySandboxComponent,
        GroovyVariableManager groovyVariableManager,
        GuildManager guildManager,
        GuildPropertyManager guildPropertyManager,
        HibernateComponent hibernateComponent,
        HttpServerManager httpServerManager,
        JxpBackend jxpBackend,
        LoginManager loginManager,
        MessageService messageService,
        PlayableContainerManager playableContainerManager,
        QueryBuilderFactory queryBuilderFactory,
        SecurityManager securityManager,
        ShardManager shardManager,
        SpotifyApi.Builder spotifyApiBuilder,
        SpotifyComponent spotifyComponent,
        SpringPropertiesConfig springPropertiesConfig,
        VersionManager versionManager,
        WidgetManager widgetManager,
        ListenerAdapter... listeners
    ) {
        this.mainInstance = mainInstance;
        this.audioManager = audioManager;
        this.chartService = chartService;
        this.executionQueueManager = executionQueueManager;
        this.commandManager = commandManager;
        this.springBootContext = springBootContext;
        this.cronJobService = cronJobService;
        this.exceptionHandlerRegistry = exceptionHandlerRegistry;
        this.filebrokerApi = filebrokerApi;
        this.groovySandboxComponent = groovySandboxComponent;
        this.groovyVariableManager = groovyVariableManager;
        this.guildManager = guildManager;
        this.guildPropertyManager = guildPropertyManager;
        this.hibernateComponent = hibernateComponent;
        this.httpServerManager = httpServerManager;
        this.jxpBackend = jxpBackend;
        this.loginManager = loginManager;
        this.messageService = messageService;
        this.playableContainerManager = playableContainerManager;
        this.queryBuilderFactory = queryBuilderFactory;
        this.securityManager = securityManager;
        this.shardManager = shardManager;
        this.spotifyApiBuilder = spotifyApiBuilder;
        this.spotifyComponent = spotifyComponent;
        this.springPropertiesConfig = springPropertiesConfig;
        this.versionManager = versionManager;
        this.widgetManager = widgetManager;
        this.registeredListeners = listeners;
        instance = this;
    }

    public static Aiode get() {
        if (instance == null) {
            throw new IllegalStateException("Aiode not set up");
        }

        return instance;
    }

    public static boolean isInitialised() {
        return instance != null;
    }

    public static void launch() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("bash", "bash" + File.separator + "launch.sh");
        pb.inheritIO();
        pb.start();
    }

    // compiler warning is shown without cast
    @SuppressWarnings("RedundantCast")
    public static void registerListeners() {
        Aiode aiode = get();
        ShardManager shardManager = aiode.getShardManager();
        ListenerAdapter[] registeredListeners = aiode.getRegisteredListeners();
        shardManager.addEventListener((Object[]) registeredListeners);
        shardManager.setStatus(OnlineStatus.ONLINE);
        LOGGER.info("Registered listeners");
    }

    // compiler warning is shown without cast
    @SuppressWarnings("RedundantCast")
    public static void shutdownListeners() {
        Aiode aiode = get();
        LOGGER.info("Shutting down listeners");
        ShardManager shardManager = aiode.getShardManager();
        shardManager.setStatus(OnlineStatus.IDLE);
        ListenerAdapter[] registeredListeners = aiode.getRegisteredListeners();
        shardManager.removeEventListener((Object[]) registeredListeners);
    }

    /**
     * Shutdown the bot waiting for pending commands and rest actions. Note that #shutdownListeners usually should get
     * called first, as all ThreadExecutionQueues will close, meaning the CommandListener will fail. You should also be
     * careful to not call this method from within a CommandExecutionThread executed by a ThreadExecutionQueue, as this
     * method waits for those threads to finish, causing a deadlock.
     *
     * @param millisToWait    time to wait for pending actions to complete in milliseconds, after this time the bot will
     *                        quit either way
     * @param messagesToAwait list of shutdown notification messages that are being send that should first be awaited
     */
    public static void shutdown(int millisToWait, @Nullable List<CompletableFuture<Message>> messagesToAwait) {
        Aiode aiode = get();
        shuttingDown = true;
        LOGGER.info("Shutting down");
        ShardManager shardManager = aiode.getShardManager();
        CommandExecutionQueueManager executionQueueManager = aiode.getExecutionQueueManager();

        // use a daemon thread to shutdown the bot after the provided time has elapsed without keeping the application
        // running if all live threads terminate earlier
        Thread forceShutdownThread = new Thread(() -> {
            try {
                Thread.sleep(millisToWait);
            } catch (InterruptedException e) {
                return;
            }
            System.exit(0);
        });
        forceShutdownThread.setDaemon(true);
        forceShutdownThread.setName("force shutdown thread");
        forceShutdownThread.start();

        executionQueueManager.closeAll();
        executionQueueManager.cancelEnqueued();
        try {
            LOGGER.info("Waiting for commands to finish");
            executionQueueManager.joinAll(0);

            if (messagesToAwait != null && !messagesToAwait.isEmpty()) {
                for (CompletableFuture<Message> futureMessage : messagesToAwait) {
                    try {
                        futureMessage.get();
                    } catch (InterruptedException e) {
                        break;
                    } catch (ExecutionException e) {
                        continue;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            forceShutdownThread.interrupt();
            return;
        }
        LOGGER.info("Shutting down registered shutdownables");
        SHUTDOWNABLES.forEach(shutdownable -> shutdownable.shutdown(millisToWait));
        LOGGER.info("Shutting down JDA");
        shardManager.shutdown();
        LOGGER.info("Shutting down hibernate SessionFactory");
        aiode.getSessionFactory().close();
        LOGGER.info("Close spring ApplicationContext");
        aiode.getSpringBootContext().close();
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }

    public boolean isMainInstance() {
        return mainInstance;
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    public ChartService getChartService() {
        return chartService;
    }

    public CommandExecutionQueueManager getExecutionQueueManager() {
        return executionQueueManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public ConfigurableApplicationContext getSpringBootContext() {
        return springBootContext;
    }

    public CronJobService getCronJobService() {
        return cronJobService;
    }

    public ExceptionHandlerRegistry getExceptionHandlerRegistry() {
        return exceptionHandlerRegistry;
    }

    public FilebrokerApi getFilebrokerApi() {
        return filebrokerApi;
    }

    public GroovySandboxComponent getGroovySandboxComponent() {
        return groovySandboxComponent;
    }

    public GroovyVariableManager getGroovyVariableManager() {
        return groovyVariableManager;
    }

    public GuildManager getGuildManager() {
        return guildManager;
    }

    public GuildPropertyManager getGuildPropertyManager() {
        return guildPropertyManager;
    }

    public HibernateComponent getHibernateComponent() {
        return hibernateComponent;
    }

    public HttpServerManager getHttpServerManager() {
        return httpServerManager;
    }

    public JxpBackend getJxpBackend() {
        return jxpBackend;
    }

    public ListenerAdapter[] getRegisteredListeners() {
        return registeredListeners;
    }

    public LoginManager getLoginManager() {
        return loginManager;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public PlayableContainerManager getPlayableContainerManager() {
        return playableContainerManager;
    }

    public QueryBuilderFactory getQueryBuilderFactory() {
        return queryBuilderFactory;
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public SessionFactory getSessionFactory() {
        return hibernateComponent.getSessionFactory();
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public SpotifyApi.Builder getSpotifyApiBuilder() {
        return spotifyApiBuilder;
    }

    public SpotifyComponent getSpotifyComponent() {
        return spotifyComponent;
    }

    public SpringPropertiesConfig getSpringPropertiesConfig() {
        return springPropertiesConfig;
    }

    public VersionManager getVersionManager() {
        return versionManager;
    }

    public WidgetManager getWidgetManager() {
        return widgetManager;
    }
}
