package net.robinfriedli.botify.command;

public interface CommandInterceptor {

    void intercept(AbstractCommand command);

}
