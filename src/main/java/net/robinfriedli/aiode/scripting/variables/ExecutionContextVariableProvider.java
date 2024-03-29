package net.robinfriedli.aiode.scripting.variables;

import java.util.Collections;
import java.util.Map;

import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.scripting.GroovyVariableProvider;

public class ExecutionContextVariableProvider implements GroovyVariableProvider {

    @Override
    public Map<String, Object> provideVariables() {
        return ExecutionContext.Current.optional().map(ExecutionContext::getScriptParameters).orElse(Collections.emptyMap());
    }

}
