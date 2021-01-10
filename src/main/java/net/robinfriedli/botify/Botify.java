package net.robinfriedli.botify;

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
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.boot.Shutdownable;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.boot.VersionManager;
import net.robinfriedli.botify.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.boot.configurations.SpotifyComponent;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.command.widget.WidgetManager;
import net.robinfriedli.botify.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.botify.cron.CronJobService;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.exceptions.handler.ExceptionHandlerRegistry;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.scripting.GroovyVariableManager;
import net.robinfriedli.botify.servers.HttpServerManager;
import net.robinfriedli.jxp.api.JxpBackend;
import org.hibernate.SessionFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * This class offers several methods to manage the bot, such as un- / registering discord listeners or shutting down
 * or restarting the bot and serves as a registry for all major components, enabling static access or access from
 * outside of spring components.
 */
@Component
public class Botify {

    public static final Logger LOGGER = LoggerFactory.getLogger(Botify.class);

    public static final Set<Shutdownable> SHUTDOWNABLES = Sets.newHashSet();
    private static volatile boolean shuttingDown;

    private static Botify instance;

    private final AudioManager audioManager;
    private final CommandExecutionQueueManager executionQueueManager;
    private final CommandManager commandManager;
    private final ConfigurableApplicationContext springBootContext;
    private final CronJobService cronJobService;
    private final ExceptionHandlerRegistry exceptionHandlerRegistry;
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
    private final QueryBuilderFactory queryBuilderFactory;
    private final SecurityManager securityManager;
    private final ShardManager shardManager;
    private final SpotifyApi.Builder spotifyApiBuilder;
    private final SpotifyComponent spotifyComponent;
    private final SpringPropertiesConfig springPropertiesConfig;
    private final VersionManager versionManager;
    private final WidgetManager widgetManager;

    public Botify(AudioManager audioManager,
                  CommandExecutionQueueManager executionQueueManager,
                  CommandManager commandManager,
                  ConfigurableApplicationContext springBootContext,
                  CronJobService cronJobService,
                  ExceptionHandlerRegistry exceptionHandlerRegistry,
                  GroovySandboxComponent groovySandboxComponent,
                  GroovyVariableManager groovyVariableManager,
                  GuildManager guildManager,
                  GuildPropertyManager guildPropertyManager,
                  HibernateComponent hibernateComponent,
                  HttpServerManager httpServerManager,
                  JxpBackend jxpBackend,
                  LoginManager loginManager,
                  MessageService messageService,
                  QueryBuilderFactory queryBuilderFactory,
                  SecurityManager securityManager,
                  ShardManager shardManager,
                  SpotifyApi.Builder spotifyApiBuilder,
                  SpotifyComponent spotifyComponent,
                  SpringPropertiesConfig springPropertiesConfig,
                  VersionManager versionManager,
                  WidgetManager widgetManager,
                  ListenerAdapter... listeners) {
        this.audioManager = audioManager;
        this.executionQueueManager = executionQueueManager;
        this.commandManager = commandManager;
        this.springBootContext = springBootContext;
        this.cronJobService = cronJobService;
        this.exceptionHandlerRegistry = exceptionHandlerRegistry;
        this.groovySandboxComponent = groovySandboxComponent;
        this.groovyVariableManager = groovyVariableManager;
        this.guildManager = guildManager;
        this.guildPropertyManager = guildPropertyManager;
        this.hibernateComponent = hibernateComponent;
        this.httpServerManager = httpServerManager;
        this.jxpBackend = jxpBackend;
        this.loginManager = loginManager;
        this.messageService = messageService;
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

    public static Botify get() {
        if (instance == null) {
            throw new IllegalStateException("Botify not set up");
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
        Botify botify = get();
        ShardManager shardManager = botify.getShardManager();
        ListenerAdapter[] registeredListeners = botify.getRegisteredListeners();
        shardManager.addEventListener((Object[]) registeredListeners);
        shardManager.setStatus(OnlineStatus.ONLINE);
        LOGGER.info("Registered listeners");
    }

    // compiler warning is shown without cast
    @SuppressWarnings("RedundantCast")
    public static void shutdownListeners() {
        Botify botify = get();
        LOGGER.info("Shutting down listeners");
        ShardManager shardManager = botify.getShardManager();
        shardManager.setStatus(OnlineStatus.IDLE);
        ListenerAdapter[] registeredListeners = botify.getRegisteredListeners();
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
        Botify botify = get();
        shuttingDown = true;
        LOGGER.info("Shutting down");
        ShardManager shardManager = botify.getShardManager();
        CommandExecutionQueueManager executionQueueManager = botify.getExecutionQueueManager();

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
        botify.getSessionFactory().close();
        LOGGER.info("Close spring ApplicationContext");
        botify.getSpringBootContext().close();
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }

    public AudioManager getAudioManager() {
        return audioManager;
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
