package net.robinfriedli.botify.exceptions.handler;

import net.robinfriedli.botify.util.ClassDescriptorNode;

public interface ExceptionHandler<T extends Throwable> extends ClassDescriptorNode {

    /**
     * @return the type of exception to handle, also includes subtypes.
     */
    Class<T> getType();

    /**
     * Handle a thrown exception that applies to the this ExceptionHandler. Each exception will only be handled by one handler.
     * As soon as a handler returns {@link Result#HANDLED} no other handlers are executed, not even for causes of the exception.
     * When an exception occurs the {@link ExceptionHandlerExecutor} may be used to call the {@link ExceptionHandlerRegistry}
     * to find all applicable handlers for the type of the exception. The found handlers are ordered by applicability,
     * handlers that handle an exception type closer to the type of the thrown exception (fewer inheritance levels apart)
     * are more applicable than handlers handling a superclass, handlers with the same applicability are ordered by {@link #getPriority()}.
     * The process is repeated for causes of the exception until a handler returns {@link Result#HANDLED}.
     * <p>
     * Naturally, this method should not throw an exception.
     *
     * @param uncaughtException the uncaught exception
     * @param exceptionToHandle the exception of the type this ExceptionHandler handlers, this is either the same as
     *                          <code>uncaughtException</code> or a cause thereof
     * @return the {@link Result} of this invocation.
     */
    Result handleException(Throwable uncaughtException, T exceptionToHandle);

    /**
     * @return priority used to decide which ExceptionHandler to call first if several equally applicable handlers were found
     * (e.g. if several handlers for the same type exists or two handlers for different super types of equal inheritance level,
     * for instance a class implementing two interfaces). Since the integer is used for sorting, a lower integer means a
     * higher priority.
     */
    default int getPriority() {
        return Integer.MAX_VALUE;
    }

    enum Result {

        /**
         * Result that indicates that the ExceptionHandler handled the exception and the {@link ExceptionHandlerExecutor}
         * may return.
         */
        HANDLED,
        /**
         * Result that indicates that the ExceptionHandler has not handled the exception and the {@link ExceptionHandlerExecutor}
         * should try the next handler or call {@link ExceptionHandlerExecutor#handleUnhandled(Throwable)}.
         */
        UNHANDLED,
        /**
         * Result that indicates that the {@link ExceptionHandlerExecutor} should skip this exception type and directly
         * go to the cause exception.
         */
        SKIP_TO_CAUSE

    }

}
