package net.robinfriedli.aiode.scripting;

import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.concurrent.ThreadContext;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.persist.StaticSessionProvider;

public class ScriptCommandRunner {

    private final CommandManager commandManager;

    public ScriptCommandRunner(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    public void run(String command) {
        StaticSessionProvider.consumeSession(session -> {
            CommandContext commandContext;
            ExecutionContext oldExecutionContext;
            if (ThreadContext.Current.optional(CommandContext.class).isPresent()) {
                CommandContext currentContext = ThreadContext.Current.require(CommandContext.class);
                oldExecutionContext = currentContext;
                commandContext = currentContext.fork(command, session);
            } else if (ExecutionContext.Current.isSet()) {
                ExecutionContext executionContext = ExecutionContext.Current.require();
                oldExecutionContext = executionContext;
                commandContext = new CommandContext(
                    executionContext.getGuild(),
                    executionContext.getGuildContext(),
                    executionContext.getJda(),
                    executionContext.getMember(),
                    command,
                    executionContext.getSessionFactory(),
                    executionContext.getSpotifyApiBuilder(),
                    command,
                    executionContext.getChannel(),
                    false,
                    null
                );
            } else {
                throw new InvalidCommandException("Cannot run command without execution context");
            }

            AbstractCommand commandInstance = commandManager.instantiateCommandForContext(commandContext, session, false)
                .orElseThrow(() -> new InvalidCommandException("No command found for input"));

            ThreadContext.Current.drop(ExecutionContext.class);
            ExecutionContext.Current.set(commandContext);
            try {
                commandManager.getInterceptorChainWithoutScripting().intercept(commandInstance);
            } finally {
                ThreadContext.Current.drop(ExecutionContext.class);
                ExecutionContext.Current.set(oldExecutionContext);
            }
        });
    }

}
