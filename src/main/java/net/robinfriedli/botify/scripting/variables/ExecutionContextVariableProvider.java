package net.robinfriedli.botify.scripting.variables;

import java.util.Collections;
import java.util.Map;

import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.scripting.GroovyVariableProvider;

public class ExecutionContextVariableProvider implements GroovyVariableProvider {

    @Override
    public Map<String, Object> provideVariables() {
        return ExecutionContext.Current.optional().map(ExecutionContext::getScriptParameters).orElse(Collections.emptyMap());
    }

}
