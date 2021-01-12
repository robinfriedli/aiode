package net.robinfriedli.botify.exceptions.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.robinfriedli.botify.Botify;

/**
 * Handle exceptions by calling registered {@link ExceptionHandler} implementations, (see {@link ExceptionHandlerRegistry}).
 */
public abstract class ExceptionHandlerExecutor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Finds applicable {@link ExceptionHandler}s by calling {@link ExceptionHandlerRegistry#getApplicableExceptionHandlersOrdered(Class)}
     * and calls {@link ExceptionHandler#handleException(Throwable, Throwable)}.
     *
     * @param e the exception to handle.
     * @throws Throwable any throwable, usually the input throwable propagated by {@link #handleUnhandled(Throwable)}
     */
    @SuppressWarnings("unchecked")
    public final void handleException(Throwable e) throws Throwable {
        if (Botify.isInitialised()) {
            boolean handled = false;
            try {
                Botify botify = Botify.get();
                ExceptionHandlerRegistry exceptionHandlerRegistry = botify.getExceptionHandlerRegistry();
                Throwable toHandle = e;


                outer:
                do {
                    List<ExceptionHandler<?>> applicableHandlers = exceptionHandlerRegistry.getApplicableExceptionHandlersOrdered(toHandle.getClass());

                    // safety: applicableHandlers only contains handlers that accept Throwables of the type of toHandle or a supertype thereof
                    for (@SuppressWarnings("rawtypes") ExceptionHandler handler : applicableHandlers) {
                        //noinspection unchecked
                        ExceptionHandler.Result result = handler.handleException(e, toHandle);

                        if (result == ExceptionHandler.Result.HANDLED) {
                            handled = true;
                            break outer;
                        } else if (result == ExceptionHandler.Result.SKIP_TO_CAUSE) {
                            break;
                        }
                    }
                } while ((toHandle = toHandle.getCause()) != null);
            } catch (Exception e1) {
                logger.error(String.format("Exception occurred while calling exception handlers for exception %s. Falling back to #handleUnhandled", e), e1);
                handleUnhandled(e);
                return;
            }

            if (!handled) {
                handleUnhandled(e);
            }
        } else {
            handleUnhandled(e);
        }
    }

    /**
     * Called when no {@link ExceptionHandler} handled the exception. This may simply rethrow the exception to propagate
     * it to the thread's UncaughtExceptionHandler. Be sure that implementations of this method handles unexpected exceptions
     * properly, any thrown exception replaces the handled exception.
     */
    protected abstract void handleUnhandled(Throwable e) throws Throwable;

    /**
     * ExceptionHandlerExecutor implementation that simply propagates the unhandled exception to be handled by the thread's
     * UncaughtExceptionHandler.
     */
    public static class PropagatingExecutor extends ExceptionHandlerExecutor {

        public static final PropagatingExecutor INSTANCE = new PropagatingExecutor();

        @Override
        protected void handleUnhandled(Throwable e) throws Throwable {
            throw e;
        }
    }

}
