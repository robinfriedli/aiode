package net.robinfriedli.botify.exceptions.handler;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.exceptions.ExceptionUtils;

public class TrackLoadingExceptionHandlerExecutor extends ExceptionHandlerExecutor {

    @Nullable
    private final ExecutionContext executionContext;
    @Nullable
    private final MessageChannel channel;

    public TrackLoadingExceptionHandlerExecutor(@Nullable ExecutionContext executionContext, @Nullable MessageChannel channel) {
        this.executionContext = executionContext;
        this.channel = channel;
    }

    @Override
    protected void handleUnhandled(Throwable e) {
        Logger logger = LoggerFactory.getLogger(getClass());
        try {
            ExceptionUtils.handleTrackLoadingException(e, logger, executionContext, channel);
        } catch (Exception e1) {
            logger.error("Exception while calling ExceptionUtils#handleTrackLoadingException, falling back to logging error", e1);
            logger.error("Exception in track loading thread", e);
        }
    }
}
