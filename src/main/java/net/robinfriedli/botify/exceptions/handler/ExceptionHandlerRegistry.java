package net.robinfriedli.botify.exceptions.handler;

import java.util.List;
import java.util.stream.Collectors;

import net.robinfriedli.botify.util.ClassDescriptorNode;
import org.springframework.stereotype.Component;

@Component
public class ExceptionHandlerRegistry {

    private final List<ExceptionHandler<?>> exceptionHandlers;

    public ExceptionHandlerRegistry(List<ExceptionHandler<?>> exceptionHandlers) {
        this.exceptionHandlers = exceptionHandlers;
    }

    public List<ExceptionHandler<?>> getExceptionHandlers() {
        return exceptionHandlers;
    }

    /**
     * @param exceptionType type of the exception
     * @return all applicable {@link ExceptionHandler} instances, ordered by most applicable (closest to the provided type first,
     * superclasses later) first, then using {@link ExceptionHandler#getPriority()}.
     */
    public List<ExceptionHandler<?>> getApplicableExceptionHandlersOrdered(Class<? extends Throwable> exceptionType) {
        return exceptionHandlers.stream()
            .filter(eh -> eh.getType().isAssignableFrom(exceptionType))
            .sorted(ClassDescriptorNode.<ExceptionHandler<?>>getComparator().thenComparing(ExceptionHandler::getPriority))
            .collect(Collectors.toList());
    }

}
