package net.robinfriedli.botify.command;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.wrapper.spotify.SpotifyApi;
import groovy.lang.GroovyShell;
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
import net.robinfriedli.botify.persist.interceptors.AlertAccessConfigurationModificationInterceptor;
import net.robinfriedli.botify.persist.interceptors.AlertPlaylistModificationInterceptor;
import net.robinfriedli.botify.persist.interceptors.AlertPresetCreationInterceptor;
import net.robinfriedli.botify.persist.interceptors.AlertScriptCreationInterceptor;
import net.robinfriedli.botify.persist.interceptors.EntityValidationInterceptor;
import net.robinfriedli.botify.persist.interceptors.GuildPropertyInterceptor;
import net.robinfriedli.botify.persist.interceptors.InterceptorChain;
import net.robinfriedli.botify.persist.interceptors.PlaylistItemTimestampInterceptor;
import net.robinfriedli.botify.persist.interceptors.SanitizingEntityInterceptor;
import net.robinfriedli.botify.persist.interceptors.VerifyPlaylistInterceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Provides context for a command request, including JDA information specific to this command such as the message and
 * guild where this command originated, a per-request hibernate session with applied interceptors, the history entry for
 * this command and command monitoring. The current CommandContext can be accessed statically using the
 * CommandContext.Current class from anywhere in a {@link CommandExecutionThread}
 */
public class CommandContext {

    private final Guild guild;
    private final GuildContext guildContext;
    private final JDA jda;
    private final Member member;
    private final Message message;
    private final SessionFactory sessionFactory;
    private final SpotifyApi spotifyApi;
    private final String commandBody;
    private final String id;
    private final User user;
    private Session session;
    private CommandHistory commandHistory;
    private Thread monitoringThread;
    private SpotifyService spotifyService;

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
        this.guild = guild;
        this.guildContext = guildContext;
        this.jda = jda;
        this.member = member;
        this.message = message;
        this.sessionFactory = sessionFactory;
        this.spotifyApi = spotifyApi;
        this.commandBody = commandBody;
        this.user = user;
        id = UUID.randomUUID().toString();
    }

    /**
     * @return A new CommandContext instance based on this one with a different input
     */
    public CommandContext fork(String input, Session session) {
        CommandContext commandContext = new CommandContext(guild, guildContext, jda, member, message, sessionFactory, spotifyApi, input, user);
        commandContext.session = session;
        return commandContext;
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
        if (session != null && session.isOpen()) {
            return session;
        } else {
            Session session = sessionFactory
                .withOptions()
                .interceptor(InterceptorChain.of(
                    SanitizingEntityInterceptor.class,
                    PlaylistItemTimestampInterceptor.class,
                    VerifyPlaylistInterceptor.class,
                    AlertAccessConfigurationModificationInterceptor.class,
                    AlertPlaylistModificationInterceptor.class,
                    AlertScriptCreationInterceptor.class,
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

    public void addScriptParameters(GroovyShell shell) {
        shell.setVariable("context", this);
        shell.setVariable("guildContext", guildContext);
        shell.setVariable("playback", guildContext.getPlayback());
        shell.setVariable("guild", guild);
        shell.setVariable("message", message);
        shell.setVariable("channel", getChannel());
        shell.setVariable("member", member);
        shell.setVariable("user", user);
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

        public static Optional<CommandContext> optional() {
            return Optional.ofNullable(get());
        }

    }

}
