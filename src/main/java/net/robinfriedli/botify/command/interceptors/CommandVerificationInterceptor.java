package net.robinfriedli.botify.command.interceptors;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandInterceptor;

public class CommandVerificationInterceptor implements CommandInterceptor {

    @Override
    public void intercept(AbstractCommand command) {
        command.verify();
    }
}
