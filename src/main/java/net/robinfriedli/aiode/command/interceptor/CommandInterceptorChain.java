package net.robinfriedli.aiode.command.interceptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.List;

import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.aiode.util.InjectorService;

/**
 * Class used to create the chain of {@link CommandInterceptor}s that carry out a command execution. Its elements are
 * added and configured in the commandInterceptors XML file. The CommandExecutionInterceptor is always added as last
 * element of the chain.
 */
public class CommandInterceptorChain implements CommandInterceptor {

    private final CommandInterceptor first;

    public CommandInterceptorChain(List<CommandInterceptorContribution> contributions) {
        Iterator<CommandInterceptorContribution> iterator = contributions.iterator();
        first = instantiate(iterator.next(), iterator);
    }

    @SuppressWarnings("unchecked")
    private static CommandInterceptor instantiate(CommandInterceptorContribution interceptorContribution,
                                                  Iterator<CommandInterceptorContribution> next) {
        Class<? extends CommandInterceptor> interceptorClass = interceptorContribution.getImplementationClass();
        Constructor<?>[] constructors = interceptorClass.getConstructors();
        if (constructors.length == 0) {
            throw new IllegalStateException(interceptorClass.getSimpleName() + " does not have any public constructors");
        }

        Constructor<CommandInterceptor> constructor = (Constructor<CommandInterceptor>) constructors[0];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        int parameterCount = constructor.getParameterCount();
        Object[] parameters = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (CommandInterceptor.class.isAssignableFrom(parameterType)) {
                if (next.hasNext()) {
                    parameters[i] = instantiate(next.next(), next);
                } else {
                    parameters[i] = new EmptyCommandInterceptor();
                }
            } else if (parameterType.equals(CommandInterceptorContribution.class)) {
                parameters[i] = interceptorContribution;
            } else {
                parameters[i] = InjectorService.get(parameterType);
            }
        }

        try {
            return constructor.newInstance(parameters);
        } catch (InstantiationException e) {
            throw new RuntimeException("Constructor " + constructor.toString() + " cannot be instantiated", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access " + constructor.toString(), e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Exception while invoking constructor " + constructor.toString(), e);
        }
    }

    @Override
    public void intercept(Command command) {
        first.intercept(command);
    }
}
