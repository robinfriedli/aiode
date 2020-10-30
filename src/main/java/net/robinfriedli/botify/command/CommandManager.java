package net.robinfriedli.botify.command;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.interceptor.CommandInterceptorChain;
import net.robinfriedli.botify.command.interceptor.interceptors.ScriptCommandInterceptor;
import net.robinfriedli.botify.concurrent.CommandExecutionTask;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.listeners.CommandListener;
import net.robinfriedli.botify.discord.listeners.EventWaiter;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.botify.entities.StoredScript;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.Abort;
import net.robinfriedli.botify.exceptions.ExceptionUtils;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Starting point of a commands life cycle after being called by the {@link CommandListener}.
 * The {@link #instantiateCommandForContext(CommandContext, Session)} is responsible for finding a
 * {@link CommandContribution} or {@link Preset} for the given {@link CommandContext} as entered by the user and
 * instantiating the {@link AbstractCommand} based on the results and {@link #runCommand(Command, ThreadExecutionQueue)}
 * passes it to the {@link CommandInterceptorChain} for execution.
 */
@Component
public class CommandManager {

    private final boolean isScriptingEnabled;
    private final Context commandContributionContext;
    private final Context commandInterceptorContext;
    private final EventWaiter eventWaiter;
    private final Logger logger;
    private final QueryBuilderFactory queryBuilderFactory;

    /**
     * The chain of interceptors to process the command
     */
    private CommandInterceptorChain interceptorChain;
    private CommandInterceptorChain interceptorChainWithoutScripting;

    public CommandManager(@Value("${botify.preferences.enableScripting}") boolean isScriptingEnabled,
                          @Value("classpath:xml-contributions/commands.xml") Resource commandResource,
                          @Value("classpath:xml-contributions/commandInterceptors.xml") Resource commandInterceptorResource,
                          EventWaiter eventWaiter,
                          JxpBackend jxpBackend,
                          QueryBuilderFactory queryBuilderFactory) {
        this.isScriptingEnabled = isScriptingEnabled;
        try {
            this.commandContributionContext = jxpBackend.createContext(commandResource.getInputStream());
            this.commandInterceptorContext = jxpBackend.createContext(commandInterceptorResource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Could not instantiate " + getClass().getSimpleName(), e);
        }
        this.eventWaiter = eventWaiter;
        this.logger = LoggerFactory.getLogger(getClass());
        this.queryBuilderFactory = queryBuilderFactory;
    }

    /**
     * Run command in the current thread. This is only used for widget actions, {@link AbstractCommand} instances trigger
     * custom script command interceptors making it unpredictable how long they would block the thread (up to 10 seconds
     * max for interceptors).
     *
     * @param command the command to run
     */
    public void runCommand(Command command) {
        ExecutionContext.Current.set(command.getContext());
        try {
            doRunCommand(command);
        } catch (Exception e) {
            ExceptionUtils.handleCommandException(e, command, logger);
        } finally {
            ThreadContext.Current.clear();
        }
    }

    /**
     * Run command in a new thread, to be enqueued in the provided {@link ThreadExecutionQueue}.
     *
     * @param command        the command to run
     * @param executionQueue the target execution queue
     */
    public void runCommand(Command command, ThreadExecutionQueue executionQueue) {
        CommandContext context = command.getContext();
        CommandExecutionTask commandExecutionTask = new CommandExecutionTask(command, executionQueue, this);

        commandExecutionTask.setName("command-execution-" + context);
        boolean queued = !executionQueue.add(commandExecutionTask, false);

        if (queued) {
            MessageService messageService = Botify.get().getMessageService();
            messageService.sendError("Executing too many commands concurrently. This command will be executed after one has finished. " +
                "You may use the abort command to cancel queued commands and interrupt running commands.", context.getChannel());
            logger.warn(String.format("Guild %s has reached the max concurrent commands limit.", context.getGuild()));
        }
    }

    public void doRunCommand(Command command) {
        try {
            if (isScriptingEnabled) {
                interceptorChain.intercept(command);
            } else {
                interceptorChainWithoutScripting.intercept(command);
            }
        } catch (Abort ignored) {
        } finally {
            command.getContext().closeSession();
        }
    }

    public Optional<AbstractCommand> instantiateCommandForContext(CommandContext context, Session session) {
        return instantiateCommandForContext(context, session, true);
    }

    public Optional<AbstractCommand> instantiateCommandForContext(CommandContext context, Session session, boolean includeScripts) {
        String commandBody = context.getCommandBody();

        if (commandBody.isBlank()) {
            return Optional.empty();
        }

        String formattedCommandInput = commandBody.toLowerCase();
        CommandContribution commandContribution = getCommandContributionForInput(commandBody);
        AbstractCommand commandInstance;
        // find a preset where the preset name matches the beginning of the command, find the longest matching preset name
        // corresponds to lower(name) = substring(lower('" + commandBody.replaceAll("'", "''") + "'), 0, length(name) + 1)
        Optional<Preset> optionalPreset = queryBuilderFactory.find(Preset.class)
            .where((cb, root) -> cb.equal(
                cb.lower(root.get("name")),
                cb.substring(cb.literal(formattedCommandInput), cb.literal(1), cb.length(root.get("name")))
            ))
            .orderBy((root, cb) -> cb.desc(cb.length(root.get("name"))))
            .build(session)
            .setMaxResults(1)
            .setCacheable(true)
            .uniqueResultOptional();

        Optional<StoredScript> optionalStoredScript;
        if (includeScripts) {
            optionalStoredScript = queryBuilderFactory.find(StoredScript.class)
                .where(((cb, root, subQueryFactory) -> cb.and(
                    cb.equal(
                        cb.lower(root.get("identifier")),
                        cb.substring(cb.literal(formattedCommandInput), cb.literal(1), cb.length(root.get("identifier")))
                    ),
                    cb.equal(
                        root.get("scriptUsage"),
                        subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk")
                            .where((cb1, root1) -> cb1.equal(root1.get("uniqueId"), "script"))
                            .build(session)
                    )
                )))
                .orderBy((root, cb) -> cb.desc(cb.length(root.get("identifier"))))
                .build(session)
                .setMaxResults(1)
                .setCacheable(true)
                .uniqueResultOptional();
        } else {
            optionalStoredScript = Optional.empty();
        }

        // there are 2 ^ 3 different combinations
        // prioritise commands over presets and presets over scripts
        // A: commandContribution exists
        // B: optionalPreset is present
        // C: optionalStoredScript is present

        // A ∧ B ∧ C
        if (commandContribution != null && optionalPreset.isPresent() && optionalStoredScript.isPresent()) {
            Preset preset = optionalPreset.get();
            String identifier = commandContribution.getIdentifier();
            StoredScript storedScript = optionalStoredScript.get();
            if (preset.getName().length() > identifier.length() && preset.getName().length() >= storedScript.getIdentifier().length()) {
                commandInstance = preset.instantiateCommand(this, context, commandBody);
            } else if (storedScript.getIdentifier().length() > identifier.length() && storedScript.getIdentifier().length() > preset.getName().length()) {
                commandInstance = storedScript.asCommand(this, context, commandBody);
            } else {
                String commandInput = commandBody.substring(identifier.length()).trim();
                commandInstance = commandContribution.instantiate(this, context, commandInput);
            }
        } else if (commandContribution != null && optionalPreset.isPresent()) {
            // A ∧ B ∧ !C
            String identifier = commandContribution.getIdentifier();
            Preset preset = optionalPreset.get();
            if (preset.getName().length() > identifier.length()) {
                commandInstance = preset.instantiateCommand(this, context, commandBody);
            } else {
                String commandInput = commandBody.substring(identifier.length()).trim();
                commandInstance = commandContribution.instantiate(this, context, commandInput);
            }
        } else if (optionalPreset.isPresent() && optionalStoredScript.isPresent()) {
            // !A ∧ B ∧ C
            Preset preset = optionalPreset.get();
            StoredScript storedScript = optionalStoredScript.get();
            if (storedScript.getIdentifier().length() > preset.getName().length()) {
                commandInstance = storedScript.asCommand(this, context, commandBody);
            } else {
                commandInstance = preset.instantiateCommand(this, context, commandBody);
            }
        } else if (commandContribution != null && optionalStoredScript.isPresent()) {
            // A ∧ !B ∧ C
            StoredScript storedScript = optionalStoredScript.get();
            String identifier = commandContribution.getIdentifier();
            if (storedScript.getIdentifier().length() > identifier.length()) {
                commandInstance = storedScript.asCommand(this, context, commandBody);
            } else {
                String commandInput = commandBody.substring(identifier.length()).trim();
                commandInstance = commandContribution.instantiate(this, context, commandInput);
            }
        } else if (optionalStoredScript.isPresent()) {
            // !A ∧ !B ∧ C
            commandInstance = optionalStoredScript.get().asCommand(this, context, commandBody);
        } else if (optionalPreset.isPresent()) {
            // !A ∧ B ∧ !C
            commandInstance = optionalPreset.get().instantiateCommand(this, context, commandBody);
        } else if (commandContribution != null) {
            // A ∧ !B ∧ !C
            String commandInput = commandBody.substring(commandContribution.getIdentifier().length()).trim();
            commandInstance = commandContribution.instantiate(this, context, commandInput);
        } else {
            // !A ∧ !B ∧ !C
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

    public List<CommandContribution> getCommandContributions() {
        return commandContributionContext.getInstancesOf(CommandContribution.class);
    }

    public CommandContribution getCommandContribution(String name) {
        return commandContributionContext.query(and(
            instanceOf(CommandContribution.class),
            attribute("identifier").is(name)
        ), CommandContribution.class).getOnlyResult();
    }

    public CommandInterceptorChain getInterceptorChain() {
        return interceptorChain;
    }

    public Context getCommandContributionContext() {
        return commandContributionContext;
    }

    public CommandInterceptorChain getInterceptorChainWithoutScripting() {
        return interceptorChainWithoutScripting;
    }

    public Context getCommandInterceptorContext() {
        return commandInterceptorContext;
    }

    public List<CommandInterceptorContribution> getCommandInterceptorContributions(Class<?>... skippedInterceptors) {
        Set<String> skipped = Arrays.stream(skippedInterceptors).map(Class::getName).collect(Collectors.toSet());
        return getCommandInterceptorContext().query(and(
            instanceOf(CommandInterceptorContribution.class),
            xmlElement -> !skipped.contains(xmlElement.getAttribute("implementation").getValue())
        ), CommandInterceptorContribution.class).collect();
    }

    public void initializeInterceptorChain() {
        interceptorChain = new CommandInterceptorChain(getCommandInterceptorContributions());
        interceptorChainWithoutScripting = new CommandInterceptorChain(getCommandInterceptorContributions(
            ScriptCommandInterceptor.ScriptCommandInterceptorPreExecution.class,
            ScriptCommandInterceptor.ScriptCommandInterceptorFinalizer.class
        ));
    }

    public EventWaiter getEventWaiter() {
        return eventWaiter;
    }

}
