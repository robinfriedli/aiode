package net.robinfriedli.botify.concurrent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.robinfriedli.botify.audio.spotify.SpotifyService;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.persist.interceptors.AlertAccessConfigurationModificationInterceptor;
import net.robinfriedli.botify.persist.interceptors.AlertPlaylistModificationInterceptor;
import net.robinfriedli.botify.persist.interceptors.AlertPresetCreationInterceptor;
import net.robinfriedli.botify.persist.interceptors.AlertScriptModificationInterceptor;
import net.robinfriedli.botify.persist.interceptors.EntityValidationInterceptor;
import net.robinfriedli.botify.persist.interceptors.GuildPropertyInterceptor;
import net.robinfriedli.botify.persist.interceptors.InterceptorChain;
import net.robinfriedli.botify.persist.interceptors.PlaylistItemTimestampInterceptor;
import net.robinfriedli.botify.persist.interceptors.SanitizingEntityInterceptor;
import net.robinfriedli.botify.persist.interceptors.VerifyPlaylistInterceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Provides context for the task executed by the current thread. This is setup by {@link CommandExecutionTask} upon
 * execution.
 */
public class ExecutionContext implements CloseableThreadContext, ForkableThreadContext<ExecutionContext> {

    protected final Guild guild;
    protected final GuildContext guildContext;
    protected final JDA jda;
    protected final Member member;
    protected final SessionFactory sessionFactory;
    protected final SpotifyApi spotifyApi;
    protected final SpotifyApi.Builder spotifyApiBuilder;
    protected final String id;
    protected final TextChannel textChannel;
    protected final User user;
    protected Session session;
    protected SpotifyService spotifyService;
    protected Thread thread;

    public ExecutionContext(
        Guild guild,
        GuildContext guildContext,
        JDA jda,
        Member member,
        SessionFactory sessionFactory,
        SpotifyApi.Builder spotifyApiBuilder,
        TextChannel textChannel
    ) {
        this(
            guild,
            guildContext,
            jda,
            member,
            sessionFactory,
            spotifyApiBuilder.build(),
            spotifyApiBuilder,
            UUID.randomUUID().toString(),
            textChannel,
            member.getUser(),
            null
        );
    }

    // constructor intended for making copies
    public ExecutionContext(
        Guild guild,
        GuildContext guildContext,
        JDA jda,
        Member member,
        SessionFactory sessionFactory,
        SpotifyApi.Builder spotifyApiBuilder,
        String id,
        TextChannel textChannel,
        User user
    ) {
        this(
            guild,
            guildContext,
            jda,
            member,
            sessionFactory,
            spotifyApiBuilder.build(),
            spotifyApiBuilder,
            id,
            textChannel,
            user,
            null
        );
    }

    public ExecutionContext(
        Guild guild,
        GuildContext guildContext,
        JDA jda,
        Member member,
        SessionFactory sessionFactory,
        SpotifyApi spotifyApi,
        SpotifyApi.Builder spotifyApiBuilder,
        String id,
        TextChannel textChannel,
        User user,
        SpotifyService spotifyService
    ) {
        this.guild = guild;
        this.guildContext = guildContext;
        this.jda = jda;
        this.member = member;
        this.sessionFactory = sessionFactory;
        this.spotifyApi = spotifyApi;
        this.spotifyApiBuilder = spotifyApiBuilder;
        this.id = id;
        this.textChannel = textChannel;
        this.user = user;
        this.spotifyService = spotifyService;
    }

    @Nullable
    public <E extends ExecutionContext> E unwrap(Class<E> contextType) {
        if (contextType.isInstance(this)) {
            return contextType.cast(this);
        }

        return null;
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

    public TextChannel getChannel() {
        return textChannel;
    }

    public JDA getJda() {
        return jda;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    /**
     * @return the hibernate persist Session associated with this ExecutionContext. Creates a new session the first time
     * it's called. NOTE: do never use this method outside of the thread associated with this ExecutionContext. Sessions
     * are NOT thread safe objects. also the instantiation of the InterceptorChain will fail if the current thread has
     * no ExecutionContext setup since some Interceptors try to inject the current context.
     */
    public Session getSession() {
        ExecutionContext executionContext = Current.get();
        if (executionContext != this) {
            throw new IllegalStateException("Invoking ExecutionContext#getSession from a thread that is not associated with this ExecutionContext. " +
                "It is not safe to pass Sessions between threads, as such this method can only be called by the thread " +
                "where this ExecutionContext is installed as current ExecutionContext. You may use ExecutionContext#threadSafe to create a thread safe copy.");
        }

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
                    AlertScriptModificationInterceptor.class,
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

    public Map<String, Object> getScriptParameters() {
        return Map.of(
            "context", this,
            "guildContext", guildContext,
            "playback", guildContext.getPlayback(),
            "guild", guild,
            "channel", getChannel(),
            "member", member,
            "user", user
        );
    }

    @Override
    public void setThread(Thread thread) {
        if (this.thread != null && this.thread != thread) {
            throw new IllegalStateException(String.format("Cannot install ExecutionContext for thread '%s', already installed on thread '%s'. Use ExecutionContext#threadSafe to create a thread safe copy.", thread, this.thread));
        }

        this.thread = thread;
    }

    @Override
    public ExecutionContext fork() {
        return new ExecutionContext(guild, guildContext, jda, member, sessionFactory, spotifyApiBuilder, id, textChannel, user);
    }

    @Override
    public void close() {
        closeSession();
    }

    /**
     * Static access to the current CommandContext, if the current thread is a CommandExecutionThread. Shortcut for
     * {@link ThreadContext.Current}
     */
    public static class Current {

        public static void set(ExecutionContext executionContext) {
            ThreadContext.Current.install(executionContext);
        }

        @Nullable
        public static ExecutionContext get() {
            return optional().orElse(null);
        }

        public static ExecutionContext require() {
            ExecutionContext commandContext = get();

            if (commandContext == null) {
                throw new IllegalStateException("No ExecutionContext set up for current thread " + Thread.currentThread().getName());
            }

            return commandContext;
        }

        public static boolean isSet() {
            return get() != null;
        }

        public static Optional<ExecutionContext> optional() {
            return ThreadContext.Current.optional(ExecutionContext.class);
        }

        public static boolean is(Class<? extends ExecutionContext> contextType) {
            ExecutionContext executionContext = get();
            return contextType.isInstance(executionContext);
        }

        @Nullable
        public static <E extends ExecutionContext> E getUnwrap(Class<E> contextType) {
            ExecutionContext executionContext = get();

            if (executionContext != null) {
                return executionContext.unwrap(contextType);
            }

            return null;
        }

        public static <E extends ExecutionContext> E requireUnwrap(Class<E> contextType) {
            ExecutionContext executionContext = require();
            E unwrap = executionContext.unwrap(contextType);

            if (unwrap == null) {
                throw new IllegalStateException(String.format("Current execution context is not of type '%s' but '%s'", contextType.getName(), executionContext.getClass().getName()));
            }

            return unwrap;
        }

        public static <E extends ExecutionContext> Optional<E> optionalWrapped(Class<E> contextType) {
            return optional().map(ec -> ec.unwrap(contextType));
        }

    }

}
