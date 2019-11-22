package net.robinfriedli.botify;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Sets;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.boot.Shutdownable;
import net.robinfriedli.botify.boot.VersionManager;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.cron.CronJobService;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.jxp.api.JxpBackend;
import org.hibernate.SessionFactory;

public class Botify {

    public static final Logger LOGGER = LoggerFactory.getLogger(Botify.class);

    public static final Set<Shutdownable> SHUTDOWNABLES = Sets.newHashSet();

    private static Botify instance;

    private final AudioManager audioManager;
    private final CommandExecutionQueueManager executionQueueManager;
    private final CommandManager commandManager;
    private final CronJobService cronJobService;
    private final GuildManager guildManager;
    private final GuildPropertyManager guildPropertyManager;
    private final JxpBackend jxpBackend;
    private final ListenerAdapter[] registeredListeners;
    private final LoginManager loginManager;
    private final MessageService messageService;
    private final SecurityManager securityManager;
    private final SessionFactory sessionFactory;
    private final ShardManager shardManager;
    private final SpotifyApi.Builder spotifyApiBuilder;
    private final VersionManager versionManager;

    public Botify(AudioManager audioManager,
                  CommandExecutionQueueManager executionQueueManager,
                  CommandManager commandManager,
                  CronJobService cronJobService,
                  GuildManager guildManager,
                  GuildPropertyManager guildPropertyManager,
                  JxpBackend jxpBackend,
                  LoginManager loginManager,
                  MessageService messageService,
                  SecurityManager securityManager,
                  SessionFactory sessionFactory,
                  ShardManager shardManager,
                  SpotifyApi.Builder spotifyApiBuilder,
                  VersionManager versionManager,
                  ListenerAdapter... listeners) {
        this.audioManager = audioManager;
        this.executionQueueManager = executionQueueManager;
        this.commandManager = commandManager;
        this.cronJobService = cronJobService;
        this.guildManager = guildManager;
        this.guildPropertyManager = guildPropertyManager;
        this.jxpBackend = jxpBackend;
        this.loginManager = loginManager;
        this.messageService = messageService;
        this.securityManager = securityManager;
        this.sessionFactory = sessionFactory;
        this.shardManager = shardManager;
        this.spotifyApiBuilder = spotifyApiBuilder;
        this.versionManager = versionManager;
        this.registeredListeners = listeners;
        instance = this;
    }

    public static Botify get() {
        if (instance == null) {
            throw new IllegalStateException("Botify not set up");
        }

        return instance;
    }

    public static void launch() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("bash", "bash" + File.separator + "launch.sh");
        pb.inheritIO();
        pb.start();
    }

    public static void registerListeners() {
        Botify botify = get();
        ShardManager shardManager = botify.getShardManager();
        ListenerAdapter[] registeredListeners = botify.getRegisteredListeners();
        shardManager.addEventListener((Object[]) registeredListeners);
        shardManager.setStatus(OnlineStatus.ONLINE);
        LOGGER.info("Registered listeners");
    }

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
     * @param millisToWait time to wait for pending actions to complete in milliseconds, after this time the bot will
     *                     quit either way
     */
    public static void shutdown(int millisToWait) {
        Botify botify = get();
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
        try {
            LOGGER.info("Waiting for commands to finish");
            executionQueueManager.joinAll();
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

    public CronJobService getCronJobService() {
        return cronJobService;
    }

    public GuildManager getGuildManager() {
        return guildManager;
    }

    public GuildPropertyManager getGuildPropertyManager() {
        return guildPropertyManager;
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

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public SpotifyApi.Builder getSpotifyApiBuilder() {
        return spotifyApiBuilder;
    }

    public VersionManager getVersionManager() {
        return versionManager;
    }
}
