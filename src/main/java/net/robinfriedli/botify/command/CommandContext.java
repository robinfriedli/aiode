package net.robinfriedli.botify.command;

import java.util.UUID;

import javax.annotation.Nullable;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.concurrent.CommandExecutionThread;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.interceptors.AlertAccessConfigurationModificationInterceptor;
import net.robinfriedli.botify.interceptors.AlertPlaylistModificationInterceptor;
import net.robinfriedli.botify.interceptors.AlertPresetCreationInterceptor;
import net.robinfriedli.botify.interceptors.GuildPropertyInterceptor;
import net.robinfriedli.botify.interceptors.InterceptorChain;
import net.robinfriedli.botify.interceptors.PlaylistItemTimestampListener;
import net.robinfriedli.botify.interceptors.VerifyPlaylistListener;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Provides context for a command request, including JDA information specific to this command such as the message and
 * guild where this command originated, a per-request hibernate session with applied interceptors, the history entry for
 * this command and command monitoring. The current CommandContext can be accessed statically using the
 * CommandContext.Current class from anywhere in a {@link CommandExecutionThread}
 */
public class CommandContext {

    private final String commandBody;
    private final Message message;
    private final SessionFactory sessionFactory;
    private final SpotifyApi spotifyApi;
    private final GuildContext guildContext;
    private final String id;
    private Session session;
    private CommandHistory commandHistory;
    private Thread monitoringThread;
    private SpotifyService spotifyService;

    public CommandContext(String commandBody, Message message, SessionFactory sessionFactory, SpotifyApi spotifyApi, GuildContext guildContext) {
        this.sessionFactory = sessionFactory;
        this.spotifyApi = spotifyApi;
        this.guildContext = guildContext;
        this.commandBody = commandBody;
        this.message = message;
        id = UUID.randomUUID().toString();
    }

    public Message getMessage() {
        return message;
    }

    public User getUser() {
        return message.getAuthor();
    }

    public Guild getGuild() {
        return message.getGuild();
    }

    public MessageChannel getChannel() {
        return message.getChannel();
    }

    public JDA getJda() {
        return message.getJDA();
    }

    public String getCommandBody() {
        return commandBody;
    }

    public void registerMonitoring(Thread monitoringThread) {
        this.monitoringThread = monitoringThread;
    }

    public void startMonitoring() {
        if (monitoringThread != null) {
            monitoringThread.start();
        }
    }

    public void interruptMonitoring() {
        if (monitoringThread != null) {
            monitoringThread.interrupt();
        }
    }

    public Session getSession() {
        if (session != null && session.isOpen()) {
            return session;
        } else {
            Session session = sessionFactory
                .withOptions()
                .interceptor(InterceptorChain.of(
                    PlaylistItemTimestampListener.class,
                    VerifyPlaylistListener.class,
                    AlertAccessConfigurationModificationInterceptor.class,
                    AlertPlaylistModificationInterceptor.class,
                    AlertPresetCreationInterceptor.class,
                    GuildPropertyInterceptor.class
                ))
                .openSession();
            this.session = session;
            return session;
        }
    }

    public void closeSession() {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }

    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
    }

    public CommandHistory getCommandHistory() {
        return commandHistory;
    }

    public void setCommandHistory(CommandHistory commandHistory) {
        this.commandHistory = commandHistory;
    }

    public GuildContext getGuildContext() {
        return guildContext;
    }

    public String getId() {
        return id;
    }

    public String toString() {
        return "CommandContext@" + id;
    }

    public SpotifyService getSpotifyService() {
        if (spotifyService == null) {
            spotifyService = new SpotifyService(spotifyApi);
        }

        return spotifyService;
    }

    /**
     * Static access to the current CommandContext, if the current thread is a CommandExecutionThread
     */
    public static class Current {

        private static final ThreadLocal<CommandContext> COMMAND_CONTEXT = new ThreadLocal<>();

        public static void set(CommandContext commandContext) {
            Current.COMMAND_CONTEXT.set(commandContext);
        }

        @Nullable
        public static CommandContext get() {
            return COMMAND_CONTEXT.get();
        }

        public static CommandContext require() {
            CommandContext commandContext = get();

            if (commandContext == null) {
                throw new IllegalStateException("No CommandContext set up for current thread " + Thread.currentThread().getName());
            }

            return commandContext;
        }

        public static boolean isSet() {
            return get() != null;
        }

    }

}
