package net.robinfriedli.aiode.command;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.robinfriedli.aiode.concurrent.CommandExecutionTask;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.entities.CommandHistory;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.jetbrains.annotations.Nullable;
import se.michaelthelin.spotify.SpotifyApi;

/**
 * Provides context for a command request, including JDA information specific to this command such as the message and
 * guild where this command originated, a per-request hibernate session with applied interceptors, the history entry for
 * this command and command monitoring. The current CommandContext can be accessed statically using the
 * CommandContext.Current class from anywhere in a {@link CommandExecutionTask}
 */
public class CommandContext extends ExecutionContext {

    private final String message;
    private final String commandBody;
    private final boolean isSlashCommand;
    @Nullable
    private final InteractionHook interactionHook;
    private CommandHistory commandHistory;
    private Future<?> monitoring;

    public CommandContext(
        SlashCommandEvent event,
        GuildContext guildContext,
        SessionFactory sessionFactory,
        SpotifyApi.Builder spotifyApiBuilder,
        String commandBody
    ) {
        this(
            event.getGuild(),
            guildContext,
            event.getJDA(),
            Objects.requireNonNull(event.getMember()),
            event.getCommandString(),
            sessionFactory,
            spotifyApiBuilder,
            commandBody,
            event.getTextChannel(),
            true,
            event.getInteraction().getHook()
        );
    }

    public CommandContext(
        GuildMessageReceivedEvent event,
        GuildContext guildContext,
        SessionFactory sessionFactory,
        SpotifyApi.Builder spotifyApiBuilder,
        String commandBody
    ) {
        this(
            event.getGuild(),
            guildContext,
            event.getJDA(),
            Objects.requireNonNull(event.getMember()),
            event.getMessage().getContentDisplay(),
            sessionFactory,
            spotifyApiBuilder,
            commandBody,
            event.getChannel(),
            false,
            null
        );
    }

    public CommandContext(
        GuildMessageReactionAddEvent event,
        GuildContext guildContext,
        String message,
        SessionFactory sessionFactory,
        SpotifyApi.Builder spotifyApiBuilder,
        String commandBody
    ) {
        this(
            event.getGuild(),
            guildContext,
            event.getJDA(),
            event.getMember(),
            message,
            sessionFactory,
            spotifyApiBuilder,
            commandBody,
            event.getChannel(),
            false,
            null
        );
    }

    // constructor intended for making copies
    public CommandContext(
        Guild guild,
        GuildContext guildContext,
        JDA jda,
        Member member,
        String message,
        SessionFactory sessionFactory,
        SpotifyApi.Builder spotifyApiBuilder,
        String commandBody,
        String id,
        TextChannel textChannel,
        User user,
        boolean isSlashCommand,
        @Nullable InteractionHook interactionHook
    ) {
        super(
            guild,
            guildContext,
            jda,
            member,
            sessionFactory,
            spotifyApiBuilder,
            id,
            textChannel,
            user
        );
        this.message = message;
        this.commandBody = commandBody;
        this.isSlashCommand = isSlashCommand;
        this.interactionHook = interactionHook;
    }

    public CommandContext(
        Guild guild,
        GuildContext guildContext,
        JDA jda,
        Member member,
        String message,
        SessionFactory sessionFactory,
        SpotifyApi.Builder spotifyApiBuilder,
        String commandBody,
        TextChannel textChannel,
        boolean isSlashCommand,
        @Nullable InteractionHook interactionHook
    ) {
        super(guild, guildContext, jda, member, sessionFactory, spotifyApiBuilder, textChannel);
        this.message = message;
        this.commandBody = commandBody;
        this.isSlashCommand = isSlashCommand;
        this.interactionHook = interactionHook;
    }

    /**
     * @return A new CommandContext instance based on this one with a different input
     */
    public CommandContext fork(String input, Session session) {
        CommandContext commandContext = new CommandContext(
            guild,
            guildContext,
            jda,
            member,
            message,
            sessionFactory,
            spotifyApiBuilder,
            input,
            textChannel,
            isSlashCommand,
            interactionHook
        );
        commandContext.session = session;
        return commandContext;
    }

    public String getMessage() {
        return message;
    }

    public String getCommandBody() {
        return commandBody;
    }

    public CommandHistory getCommandHistory() {
        return commandHistory;
    }

    public boolean isSlashCommand() {
        return isSlashCommand;
    }

    @Nullable
    public InteractionHook getInteractionHook() {
        return interactionHook;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Map<String, Object> getScriptParameters() {
        Map<String, Object> executionContextParameters = super.getScriptParameters();
        Map.Entry[] executionContextEntries = executionContextParameters.entrySet().toArray(new Map.Entry[0]);
        Map.Entry[] entryArray = Arrays.copyOf(executionContextEntries, executionContextEntries.length + 1);
        entryArray[entryArray.length - 1] = Map.entry("message", message);
        return Map.ofEntries(entryArray);
    }

    @Override
    public ExecutionContext fork() {
        return new CommandContext(
            guild,
            guildContext,
            jda,
            member,
            message,
            sessionFactory,
            spotifyApiBuilder,
            commandBody,
            id,
            textChannel,
            user,
            isSlashCommand,
            interactionHook
        );
    }

}
