package net.robinfriedli.botify.scripting.variables;

import java.util.Collections;
import java.util.Map;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.botify.scripting.GroovyVariableProvider;

public class ThreadContextVariableProvider implements GroovyVariableProvider {

    @Override
    public Map<String, ?> provideVariables() {
        return ThreadContext.Current.optional(Command.class).map(command -> {
            if (command instanceof AbstractCommand) {
                return Map.of(
                    "command", command,
                    "input", ((AbstractCommand) command).getCommandInput()
                );
            } else {
                return Map.of("command", command);
            }
        }).orElse(Collections.emptyMap());
    }
}
