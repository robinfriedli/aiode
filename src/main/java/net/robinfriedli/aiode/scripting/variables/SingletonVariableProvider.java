package net.robinfriedli.aiode.scripting.variables;

import java.util.Map;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.scripting.GroovyVariableProvider;

public class SingletonVariableProvider implements GroovyVariableProvider {

    @Override
    public Map<String, Object> provideVariables() {
        Aiode aiode = Aiode.get();
        return Map.of(
            "messages", aiode.getMessageService(),
            "securityManager", aiode.getSecurityManager(),
            "audioManager", aiode.getAudioManager()
        );
    }

}
