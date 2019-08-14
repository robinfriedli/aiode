package net.robinfriedli.botify;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.properties.GuildPropertyManager;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.jxp.api.JxpBackend;
import org.hibernate.SessionFactory;

public class Botify {


    public static final Logger LOGGER = LoggerFactory.getLogger(Botify.class);

    private static Botify instance;

    private final AudioManager audioManager;
    private final CommandExecutionQueueManager executionQueueManager;
    private final CommandManager commandManager;
    private final GuildManager guildManager;
    private final GuildPropertyManager guildPropertyManager;
    private final JDA jda;
    private final JxpBackend jxpBackend;
    private final ListenerAdapter[] registeredListeners;
    private final LoginManager loginManager;
    private final SecurityManager securityManager;
    private final SessionFactory sessionFactory;
    private final SpotifyApi.Builder spotifyApiBuilder;

    public Botify(AudioManager audioManager,
                  CommandExecutionQueueManager executionQueueManager,
                  CommandManager commandManager,
                  GuildManager guildManager,
                  GuildPropertyManager guildPropertyManager,
                  JDA jda,
                  JxpBackend jxpBackend,
                  LoginManager loginManager,
                  SecurityManager securityManager,
                  SessionFactory sessionFactory,
                  SpotifyApi.Builder spotifyApiBuilder,
                  ListenerAdapter... listeners) {
        this.audioManager = audioManager;
        this.executionQueueManager = executionQueueManager;
        this.commandManager = commandManager;
        this.guildManager = guildManager;
        this.guildPropertyManager = guildPropertyManager;
        this.jda = jda;
        this.jxpBackend = jxpBackend;
        this.loginManager = loginManager;
        this.securityManager = securityManager;
        this.sessionFactory = sessionFactory;
        this.spotifyApiBuilder = spotifyApiBuilder;
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
        ProcessBuilder pb = new ProcessBuilder("bash", "./resources/bash/launch.sh");
        pb.inheritIO();
        pb.start();
    }

    public static void registerListeners() {
        Botify botify = get();
        JDA jda = botify.getJda();
        ListenerAdapter[] registeredListeners = botify.getRegisteredListeners();
        jda.addEventListener((Object[]) registeredListeners);
        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        LOGGER.info("Registered listeners");
    }

    public static void shutdownListeners() {
        Botify botify = get();
        LOGGER.info("Shutting down listeners");
        JDA jda = botify.getJda();
        jda.getPresence().setStatus(OnlineStatus.IDLE);
        ListenerAdapter[] registeredListeners = botify.getRegisteredListeners();
        jda.removeEventListener((Object[]) registeredListeners);
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
    public static void shutdown(long millisToWait) {
        Botify botify = get();
        LOGGER.info("Shutting down");
        JDA jda = botify.getJda();
        CommandExecutionQueueManager executionQueueManager = botify.getExecutionQueueManager();
        executionQueueManager.closeAll();
        Thread shutdownThread = new Thread(() -> {
            try {
                LOGGER.info("Waiting for commands to finish");
                executionQueueManager.joinAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            LOGGER.info("Shutting down JDA");
            jda.shutdown();
        });
        shutdownThread.start();

        try {
            shutdownThread.join(millisToWait);
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while waiting for pending actions to complete. Shutting down now");
            System.exit(1);
        }

        LOGGER.info("Shutting down now");
        System.exit(0);
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

    public GuildManager getGuildManager() {
        return guildManager;
    }

    public GuildPropertyManager getGuildPropertyManager() {
        return guildPropertyManager;
    }

    public JDA getJda() {
        return jda;
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

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public SpotifyApi.Builder getSpotifyApiBuilder() {
        return spotifyApiBuilder;
    }
}
