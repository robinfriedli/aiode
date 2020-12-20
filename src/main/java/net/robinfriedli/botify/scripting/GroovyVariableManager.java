package net.robinfriedli.botify.scripting;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import groovy.lang.GroovyShell;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.botify.entities.xml.GenericClassContribution;
import net.robinfriedli.botify.entities.xml.GroovyVariableProviderContribution;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Provide groovy variables to the {@link TypeCheckingExtension} at compile time and apply them to GroovyShells.
 */
@Component
public class GroovyVariableManager {

    private static final String CACHE_KEY = "groovyVariableCache";

    private final List<GroovyVariableProvider> providers;

    public GroovyVariableManager(
        @Value("classpath:xml-contributions/groovyVariableProviders.xml") Resource groovyVariableProvidersResource,
        JxpBackend jxpBackend
    ) {
        try {
            Context context = jxpBackend.createContext(groovyVariableProvidersResource.getInputStream());
            providers = context
                .query(instanceOf(GroovyVariableProviderContribution.class), GroovyVariableProviderContribution.class)
                .getResultStream()
                .map(GenericClassContribution::instantiate)
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Could not instantiate " + getClass().getSimpleName(), e);
        }
    }

    @Nullable
    public Object getVariable(String identifier) {
        return getVariables().get(identifier);
    }

    public void prepareShell(GroovyShell groovyShell) {
        for (Map.Entry<String, ?> variable : getVariables().entrySet()) {
            groovyShell.setVariable(variable.getKey(), variable.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> getVariables() {
        Map<String, ?> cachedVariables = ThreadContext.Current.get(CACHE_KEY, Map.class);

        if (cachedVariables == null) {
            return initialiseVariableCache();
        }

        return cachedVariables;
    }

    private Map<String, Object> initialiseVariableCache() {
        Map<String, Object> variableMap = new HashMap<>();

        for (GroovyVariableProvider provider : providers) {
            Map<String, ?> variables = provider.provideVariables();
            for (Map.Entry<String, ?> variable : variables.entrySet()) {
                if (variableMap.putIfAbsent(variable.getKey(), variable.getValue()) != null) {
                    throw new IllegalStateException(String.format("Duplicate groovy variable '%s'", variable.getKey()));
                }
            }
        }

        ThreadContext.Current.install(CACHE_KEY, variableMap);
        return variableMap;
    }

}
