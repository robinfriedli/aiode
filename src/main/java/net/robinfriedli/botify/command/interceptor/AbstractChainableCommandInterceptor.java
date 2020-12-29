package net.robinfriedli.botify.command.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;

/**
 * CommandInterceptor extension that calls the next interceptor and handles exceptions according to the
 * {@link CommandInterceptorContribution} itself.
 */
public abstract class AbstractChainableCommandInterceptor implements CommandInterceptor {

    private final CommandInterceptorContribution contribution;
    private final CommandInterceptor next;

    protected AbstractChainableCommandInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next) {
        this.contribution = contribution;
        this.next = next;
    }

    @Override
    public void intercept(Command command) {
        try {
            performChained(command);
        } catch (Exception e) {
            if (contribution.throwException(e)) {
                throw e;
            } else {
                Logger logger = LoggerFactory.getLogger(getClass());
                logger.error("Unexpected exception in interceptor", e);
            }
        }

        next.intercept(command);
    }

    /**
     * Perform the task of this interceptor handling exceptions according to the CommandInterceptor's contribution
     */
    public abstract void performChained(Command command);

}
