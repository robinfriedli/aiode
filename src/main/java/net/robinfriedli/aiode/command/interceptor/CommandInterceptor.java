package net.robinfriedli.aiode.command.interceptor;

import net.robinfriedli.aiode.command.Command;

/**
 * Interface for classes that intercept and run the command execution. Its implementations are added and configured in
 * the commandInterceptors XML file
 */
public interface CommandInterceptor {

    void intercept(Command command);

    class EmptyCommandInterceptor implements CommandInterceptor {

        @Override
        public void intercept(Command command) {
        }
    }

}
