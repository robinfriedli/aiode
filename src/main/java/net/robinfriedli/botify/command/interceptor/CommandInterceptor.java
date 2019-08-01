package net.robinfriedli.botify.command.interceptor;

import net.robinfriedli.botify.command.AbstractCommand;

/**
 * Interface for classes that intercept and run the command execution. Its implementations are added and configured in
 * the commandInterceptors XML file
 */
public interface CommandInterceptor {

    void intercept(AbstractCommand command);

}
