package net.robinfriedli.botify.scripting.variables;

import java.util.Map;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.scripting.GroovyVariableProvider;

public class SingletonVariableProvider implements GroovyVariableProvider {

    @Override
    public Map<String, Object> provideVariables() {
        Botify botify = Botify.get();
        return Map.of(
            "messages", botify.getMessageService(),
            "securityManager", botify.getSecurityManager()
        );
    }

}
