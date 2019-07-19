package net.robinfriedli.botify.command.interceptor;

import net.robinfriedli.botify.command.AbstractCommand;

public interface CommandInterceptor {

    void intercept(AbstractCommand command);

}
