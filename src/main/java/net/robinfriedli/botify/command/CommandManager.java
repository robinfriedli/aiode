package net.robinfriedli.botify.command;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptorChain;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Main hub for commands containing all available commands. Is responsible for instantiating an {@link AbstractCommand}
 * based on the given {@link CommandContext} as entered by the user and then passes it to the {@link CommandInterceptor}s for
 * execution. Also manages active widgets.
 */
public class CommandManager {

    private final Context commandContributionContext;
    private final Context commandInterceptorContext;
    private final Logger logger;
    private final SessionFactory sessionFactory;

    /**
     * all widgets that currently listen for reactions. Only one widget of the same type per guild may be active.
     */
    private List<AbstractWidget> activeWidgets;
    /**
     * The chain of interceptors to process the command
     */
    private CommandInterceptorChain interceptorChain;

    public CommandManager(Context commandContributionContext,
                          Context commandInterceptorContext,
                          SessionFactory sessionFactory) {
        this.commandContributionContext = commandContributionContext;
        this.commandInterceptorContext = commandInterceptorContext;
        this.sessionFactory = sessionFactory;
        this.logger = LoggerFactory.getLogger(getClass());
        activeWidgets = Lists.newArrayList();
    }

    public void runCommand(CommandContext context) {
        String command = context.getCommandBody();

        if (command.isBlank()) {
            return;
        }

        CommandContribution commandContribution = getCommandContributionForInput(command);
        Optional<Preset> optionalPreset;
        AbstractCommand commandInstance;
        try (Session session = sessionFactory.openSession()) {
            // find a preset where the preset name matches the beginning of the command, find the longest matching preset name
            optionalPreset = session
                .createQuery("from " + Preset.class.getName()
                    + " where guild_id = '" + context.getGuild().getId()
                    + "' and lower(name) = substring(lower('" + command.replaceAll("'", "''") + "'), 0, length(name) + 1) " +
                    "order by length(name) desc", Preset.class)
                .setMaxResults(1)
                .uniqueResultOptional();
        }

        if (commandContribution != null && optionalPreset.isPresent()) {
            Preset preset = optionalPreset.get();
            String identifier = commandContribution.getIdentifier();
            if (preset.getName().length() > identifier.length()) {
                commandInstance = preset.instantiateCommand(this, context, command);
            } else {
                String commandInput = command.substring(identifier.length()).trim();
                commandInstance = commandContribution.instantiate(this, context, commandInput);
            }
        } else if (commandContribution != null) {
            String selectedCommand = commandContribution.getAttribute("identifier").getValue();
            String commandInput = command.substring(selectedCommand.length()).trim();
            commandInstance = commandContribution.instantiate(this, context, commandInput);
        } else if (optionalPreset.isPresent()) {
            commandInstance = optionalPreset.get().instantiateCommand(this, context, command);
        } else {
            throw new InvalidCommandException("No command or preset found.");
        }

        interceptorChain.intercept(commandInstance);
    }

    public Optional<AbstractCommand> getCommand(CommandContext commandContext, String name) {
        CommandContribution commandContribution = getCommandContribution(name);

        if (commandContribution == null) {
            return Optional.empty();
        }

        return Optional.of(commandContribution.instantiate(this, commandContext, ""));
    }

    public CommandContribution getCommandContributionForInput(String input) {
        return commandContributionContext.query(and(
            instanceOf(CommandContribution.class),
            xmlElement -> input.toLowerCase().startsWith(xmlElement.getAttribute("identifier").getValue())
        ), CommandContribution.class).getOnlyResult();
    }

    public List<AbstractCommand> getAllCommands(CommandContext commandContext) {
        List<AbstractCommand> commands = Lists.newArrayList();
        for (CommandContribution commandContribution : commandContributionContext.getInstancesOf(CommandContribution.class)) {
            commands.add(commandContribution.instantiate(this, commandContext, ""));
        }

        return commands;
    }

    public List<CommandContribution> getAllCommandContributions() {
        return commandContributionContext.getInstancesOf(CommandContribution.class);
    }

    public CommandContribution getCommandContribution(String name) {
        return commandContributionContext.query(and(
            instanceOf(CommandContribution.class),
            attribute("identifier").is(name)
        ), CommandContribution.class).getOnlyResult();
    }

    public void registerWidget(AbstractWidget widget) {
        List<AbstractWidget> toRemove = Lists.newArrayList();
        try {
            activeWidgets.stream()
                .filter(w -> widget.getGuildId().equals(w.getGuildId()))
                .filter(w -> w.getClass().equals(widget.getClass()))
                .forEach(toRemove::add);
        } catch (Throwable e) {
            // JDA weak reference might cause garbage collection issues when getting guild of message
            logger.warn("Exception while removing existing widget", e);
        }
        try {
            widget.setup();
            activeWidgets.add(widget);
            toRemove.forEach(AbstractWidget::destroy);
        } catch (UserException e) {
            new MessageService().sendError(e.getMessage(), widget.getMessage().getChannel());
        }
    }

    public Optional<AbstractWidget> getActiveWidget(String messageId) {
        return activeWidgets.stream().filter(widget -> widget.getMessage().getId().equals(messageId)).findAny();
    }

    public void removeWidget(AbstractWidget widget) {
        activeWidgets.remove(widget);
    }

    public void initializeInterceptorChain() {
        interceptorChain = new CommandInterceptorChain(commandInterceptorContext.getInstancesOf(CommandInterceptorContribution.class));
    }

}
