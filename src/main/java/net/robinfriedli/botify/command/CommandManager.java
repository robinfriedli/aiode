package net.robinfriedli.botify.command;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.interceptor.CommandInterceptorChain;
import net.robinfriedli.botify.concurrent.CommandExecutionThread;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.handlers.CommandExceptionHandler;
import net.robinfriedli.botify.listeners.CommandListener;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Starting point of a commands life cycle after being called by the {@link CommandListener}.
 * The {@link #instantiateCommandForContext(CommandContext, Session)} is responsible for finding a
 * {@link CommandContribution} or {@link Preset} for the given {@link CommandContext} as entered by the user and
 * instantiating the {@link AbstractCommand} based on the results and {@link #runCommand(AbstractCommand, ThreadExecutionQueue)}
 * passes it to the {@link CommandInterceptorChain} for execution.
 * Also serves as a registry for all active {@link AbstractWidget}.
 */
public class CommandManager {

    private final Context commandContributionContext;
    private final Context commandInterceptorContext;
    private final Logger logger;

    /**
     * The chain of interceptors to process the command
     */
    private CommandInterceptorChain interceptorChain;

    public CommandManager(Context commandContributionContext,
                          Context commandInterceptorContext) {
        this.commandContributionContext = commandContributionContext;
        this.commandInterceptorContext = commandInterceptorContext;
        this.logger = LoggerFactory.getLogger(getClass());
    }

    public void runCommand(Command command, ThreadExecutionQueue executionQueue) {
        CommandContext context = command.getContext();
        CommandExecutionThread commandExecutionThread = new CommandExecutionThread(command, executionQueue, () -> {
            try {
                interceptorChain.intercept(command);
            } finally {
                context.closeSession();
            }
        });

        commandExecutionThread.setUncaughtExceptionHandler(new CommandExceptionHandler(command, logger));
        commandExecutionThread.setName("botify command execution: " + context);
        boolean queued = !executionQueue.add(commandExecutionThread);

        if (queued) {
            MessageService messageService = Botify.get().getMessageService();
            messageService.sendError("Executing too many commands concurrently. This command will be executed after one has finished.", context.getChannel());
        }
    }

    public Optional<AbstractCommand> instantiateCommandForContext(CommandContext context, Session session) {
        String commandBody = context.getCommandBody();

        if (commandBody.isBlank()) {
            return Optional.empty();
        }

        CommandContribution commandContribution = getCommandContributionForInput(commandBody);
        AbstractCommand commandInstance;
        // find a preset where the preset name matches the beginning of the command, find the longest matching preset name
        Optional<Preset> optionalPreset = session
            .createQuery("from " + Preset.class.getName()
                + " where guild_id = '" + context.getGuild().getId()
                + "' and lower(name) = substring(lower('" + commandBody.replaceAll("'", "''") + "'), 0, length(name) + 1) " +
                "order by length(name) desc", Preset.class)
            .setMaxResults(1)
            .setCacheable(true)
            .uniqueResultOptional();

        if (commandContribution != null && optionalPreset.isPresent()) {
            Preset preset = optionalPreset.get();
            String identifier = commandContribution.getIdentifier();
            if (preset.getName().length() > identifier.length()) {
                commandInstance = preset.instantiateCommand(this, context, commandBody);
            } else {
                String commandInput = commandBody.substring(identifier.length()).trim();
                commandInstance = commandContribution.instantiate(this, context, commandInput);
            }
        } else if (commandContribution != null) {
            String selectedCommand = commandContribution.getAttribute("identifier").getValue();
            String commandInput = commandBody.substring(selectedCommand.length()).trim();
            commandInstance = commandContribution.instantiate(this, context, commandInput);
        } else if (optionalPreset.isPresent()) {
            commandInstance = optionalPreset.get().instantiateCommand(this, context, commandBody);
        } else {
            return Optional.empty();
        }

        return Optional.of(commandInstance);
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

    public void initializeInterceptorChain() {
        interceptorChain = new CommandInterceptorChain(commandInterceptorContext.getInstancesOf(CommandInterceptorContribution.class));
    }

}
