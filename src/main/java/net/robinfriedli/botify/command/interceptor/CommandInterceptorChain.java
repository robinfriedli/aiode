package net.robinfriedli.botify.command.interceptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.List;

import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.interceptor.interceptors.CommandExecutionInterceptor;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.util.Cache;

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
            if (parameterType.isAssignableFrom(CommandInterceptor.class)) {
                if (next.hasNext()) {
                    parameters[i] = instantiate(next.next(), next);
                } else {
                    parameters[i] = new CommandExecutionInterceptor(Cache.get(MessageService.class));
                }
            } else if (parameterType.equals(CommandInterceptorContribution.class)) {
                parameters[i] = interceptorContribution;
            } else {
                parameters[i] = Cache.get(parameterType);
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
