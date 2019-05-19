package net.robinfriedli.botify.command;

import org.slf4j.LoggerFactory;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.listener.AlertEventListener;
import net.robinfriedli.botify.listener.InterceptorChain;
import net.robinfriedli.botify.listener.PlaylistItemTimestampListener;
import net.robinfriedli.botify.util.ParameterContainer;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class CommandContext {

    private final String commandBody;
    private final Message message;
    private final SessionFactory sessionFactory;
    private final SpotifyApi spotifyApi;
    private final GuildContext guildContext;
    private Session session;
    private CommandHistory commandHistory;
    private Thread monitoringThread;

    public CommandContext(String namePrefix, Message message, SessionFactory sessionFactory, SpotifyApi spotifyApi, GuildContext guildContext) {
        this.sessionFactory = sessionFactory;
        this.spotifyApi = spotifyApi;
        this.guildContext = guildContext;
        this.commandBody = message.getContentDisplay().substring(namePrefix.length()).trim();
        this.message = message;
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

    public void interruptMonitoring() {
        monitoringThread.interrupt();
    }

    public Session getSession() {
        if (session != null && session.isOpen()) {
            return session;
        } else {
            ParameterContainer parameterContainer = new ParameterContainer(getChannel(), LoggerFactory.getLogger("Hibernate Interceptors"));
            Session session = sessionFactory
                .withOptions()
                .interceptor(InterceptorChain.of(parameterContainer, PlaylistItemTimestampListener.class, AlertEventListener.class))
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
}
