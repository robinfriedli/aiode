package net.robinfriedli.botify.command;

import java.util.Objects;
import java.util.concurrent.Future;

import com.wrapper.spotify.SpotifyApi;
import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.concurrent.CommandExecutionTask;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.entities.CommandHistory;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Provides context for a command request, including JDA information specific to this command such as the message and
 * guild where this command originated, a per-request hibernate session with applied interceptors, the history entry for
 * this command and command monitoring. The current CommandContext can be accessed statically using the
 * CommandContext.Current class from anywhere in a {@link CommandExecutionTask}
 */
public class CommandContext extends ExecutionContext {

    private final Message message;
    private final String commandBody;
    private CommandHistory commandHistory;
    private Future<?> monitoring;

    public CommandContext(GuildMessageReceivedEvent event,
                          GuildContext guildContext,
                          SessionFactory sessionFactory,
                          SpotifyApi spotifyApi,
                          String commandBody) {
        this(event.getGuild(), guildContext, event.getJDA(), Objects.requireNonNull(event.getMember()), event.getMessage(), sessionFactory, spotifyApi, commandBody, event.getChannel());
    }

    public CommandContext(GuildMessageReactionAddEvent event,
                          GuildContext guildContext,
                          Message message,
                          SessionFactory sessionFactory,
                          SpotifyApi spotifyApi,
                          String commandBody) {
        this(event.getGuild(), guildContext, event.getJDA(), event.getMember(), message, sessionFactory, spotifyApi, commandBody, event.getChannel());
    }

    public CommandContext(Guild guild,
                          GuildContext guildContext,
                          JDA jda,
                          Member member,
                          Message message,
                          SessionFactory sessionFactory,
                          SpotifyApi spotifyApi,
                          String commandBody,
                          TextChannel textChannel) {
        super(guild, guildContext, jda, member, sessionFactory, spotifyApi, textChannel);
        this.message = message;
        this.commandBody = commandBody;
    }

    /**
     * @return A new CommandContext instance based on this one with a different input
     */
    public CommandContext fork(String input, Session session) {
        CommandContext commandContext = new CommandContext(guild, guildContext, jda, member, message, sessionFactory, spotifyApi, input, textChannel);
        commandContext.session = session;
        return commandContext;
    }

    public Message getMessage() {
        return message;
    }

    public String getCommandBody() {
        return commandBody;
    }

    public CommandHistory getCommandHistory() {
        return commandHistory;
    }

    public void setCommandHistory(CommandHistory commandHistory) {
        this.commandHistory = commandHistory;
    }

    public void registerMonitoring(Future<?> monitoring) {
        this.monitoring = monitoring;
    }

    public void interruptMonitoring() {
        if (monitoring != null) {
            monitoring.cancel(true);
        }
    }

    public void addScriptParameters(GroovyShell shell) {
        super.addScriptParameters(shell);
        shell.setVariable("message", message);
    }

}
