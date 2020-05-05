package net.robinfriedli.botify.command;

import java.util.UUID;

import javax.annotation.Nullable;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.concurrent.CommandExecutionThread;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.interceptors.AlertAccessConfigurationModificationInterceptor;
import net.robinfriedli.botify.interceptors.AlertPlaylistModificationInterceptor;
import net.robinfriedli.botify.interceptors.AlertPresetCreationInterceptor;
import net.robinfriedli.botify.interceptors.EntityValidationInterceptor;
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

    protected final Guild guild;
    protected final GuildContext guildContext;
    protected final JDA jda;
    protected final Member member;
    protected final Message message;
    protected final SessionFactory sessionFactory;
    protected final SpotifyApi spotifyApi;
    protected final String commandBody;
    protected final String id;
    protected final User user;
    protected Session session;
    protected CommandHistory commandHistory;
    protected Thread monitoringThread;
    protected Thread registeredOnThread;
    protected SpotifyService spotifyService;

    public CommandContext(GuildMessageReceivedEvent event,
                          GuildContext guildContext,
                          SessionFactory sessionFactory,
                          SpotifyApi spotifyApi,
                          String commandBody) {
        this(event.getGuild(), guildContext, event.getJDA(), event.getMember(), event.getMessage(), sessionFactory, spotifyApi, commandBody, event.getAuthor());
    }

    public CommandContext(GuildMessageReactionAddEvent event,
                          GuildContext guildContext,
                          Message message,
                          SessionFactory sessionFactory,
                          SpotifyApi spotifyApi,
                          String commandBody) {
        this(event.getGuild(), guildContext, event.getJDA(), event.getMember(), message, sessionFactory, spotifyApi, commandBody, event.getUser());
    }

    public CommandContext(Guild guild,
                          GuildContext guildContext,
                          JDA jda,
                          Member member,
                          Message message,
                          SessionFactory sessionFactory,
                          SpotifyApi spotifyApi,
                          String commandBody,
                          User user) {
        this(guild, guildContext, jda, member, message, sessionFactory, spotifyApi, UUID.randomUUID().toString(), commandBody, user);
    }

    public CommandContext(Guild guild,
                          GuildContext guildContext,
                          JDA jda,
                          Member member,
                          Message message,
                          SessionFactory sessionFactory,
                          SpotifyApi spotifyApi,
                          String id,
                          String commandBody,
                          User user) {
        this.guild = guild;
        this.guildContext = guildContext;
        this.jda = jda;
        this.member = member;
        this.message = message;
        this.sessionFactory = sessionFactory;
        this.spotifyApi = spotifyApi;
        this.id = id;
        this.commandBody = commandBody;
        this.user = user;
    }

    public Message getMessage() {
        return message;
    }

    public User getUser() {
        return user;
    }

    public Member getMember() {
        return member;
    }

    @Nullable
    public VoiceChannel getVoiceChannel() {
        GuildVoiceState voiceState = getMember().getVoiceState();

        if (voiceState != null) {
            return voiceState.getChannel();
        }

        return null;
    }

    public Guild getGuild() {
        return guild;
    }

    public MessageChannel getChannel() {
        return message.getChannel();
    }

    public JDA getJda() {
        return jda;
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
        if (registeredOnThread != Thread.currentThread()) {
            throw new IllegalStateException("Invoking CommandContext#getSession from a thread that is not associated with this CommandContext. " +
                "It is not safe to pass Sessions between threads, as such this method can only be called by the thread " +
                "where this CommandContext is installed as current CommandContext. You may use CommandContext#threadSafe to create a thread safe copy.");
        }

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
                    GuildPropertyInterceptor.class,
                    EntityValidationInterceptor.class
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

    public ThreadSafeCommandContext threadSafe() {
        return new ThreadSafeCommandContext(guild, guildContext, jda, member, message, sessionFactory, spotifyApi, id, commandBody, user);
    }

    public void setRegisteredOnThread(Thread registeredOnThread) {
        if (this.registeredOnThread != null && this.registeredOnThread != registeredOnThread) {
            throw new IllegalStateException(String.format("Cannot install CommandContext for thread %s. " +
                    "Already installed on thread %s. Use CommandContext#threadSafe to create a thread safe copy.",
                registeredOnThread,
                this.registeredOnThread));
        }

        this.registeredOnThread = registeredOnThread;
    }

    /**
     * Static access to the current CommandContext, if the current thread is a CommandExecutionThread
     */
    public static class Current {

        private static final ThreadLocal<CommandContext> COMMAND_CONTEXT = new ThreadLocal<>();

        public static void set(CommandContext commandContext) {
            Current.COMMAND_CONTEXT.set(commandContext);
            commandContext.setRegisteredOnThread(Thread.currentThread());
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

    public static class ThreadSafeCommandContext extends CommandContext {

        public ThreadSafeCommandContext(Guild guild,
                                        GuildContext guildContext,
                                        JDA jda,
                                        Member member,
                                        Message message,
                                        SessionFactory sessionFactory,
                                        SpotifyApi spotifyApi,
                                        String id,
                                        String commandBody,
                                        User user) {
            super(guild, guildContext, jda, member, message, sessionFactory, spotifyApi, id, commandBody, user);
        }

        @Override
        public Session getSession() {
            return sessionFactory.getCurrentSession();
        }

        @Override
        public void closeSession() {
        }
    }

}
