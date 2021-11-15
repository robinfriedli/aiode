package net.robinfriedli.aiode.scripting.variables;

import java.util.Collections;
import java.util.Map;

import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.concurrent.ThreadContext;
import net.robinfriedli.aiode.scripting.GroovyVariableProvider;

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
