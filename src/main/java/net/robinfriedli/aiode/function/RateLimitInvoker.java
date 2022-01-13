package net.robinfriedli.aiode.function;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.Shutdownable;
import net.robinfriedli.aiode.boot.ShutdownableExecutorService;
import net.robinfriedli.aiode.concurrent.LoggingThreadFactory;
import net.robinfriedli.aiode.concurrent.ThreadContext;
import net.robinfriedli.aiode.exceptions.RateLimitException;
import net.robinfriedli.exec.BaseInvoker;
import net.robinfriedli.exec.Mode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Invoker that can schedule tasks given a provided rate limit. Until the rate limit is hit it executes tasks directly in
 * the current thread. Generally those invokers are global statics, when creating a temporary object make sure to call
 * close() when it is no longer used to avoid leaking memory.
 */
public class RateLimitInvoker extends BaseInvoker {

    public static final RateLimiterRegistry RATE_LIMITER_REGISTRY = RateLimiterRegistry.ofDefaults();

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitInvoker.class);

    private final RateLimiter rateLimiter;
    private final ScheduledExecutorService rateLimitQueueExecutor;
    private final Shutdownable shutdownable;

    public RateLimitInvoker(String identifier, int limitForPeriod, Duration period) {
        this(identifier, limitForPeriod, period, Duration.ofMinutes(5));
    }

    public RateLimitInvoker(String identifier, int limitForPeriod, Duration period, Duration timeout) {
        RateLimiterConfig config = RateLimiterConfig
            .custom()
            .limitForPeriod(limitForPeriod)
            .limitRefreshPeriod(period)
            .timeoutDuration(timeout)
            .build();

        rateLimiter = RATE_LIMITER_REGISTRY.rateLimiter(identifier, config);

        rateLimitQueueExecutor = Executors.newScheduledThreadPool(0, new LoggingThreadFactory(identifier + "-rate_limit_queue_executor"));
        shutdownable = new ShutdownableExecutorService(rateLimitQueueExecutor);

        Aiode.SHUTDOWNABLES.add(shutdownable);
    }

    public void close() {
        rateLimitQueueExecutor.shutdown();
        Aiode.SHUTDOWNABLES.remove(shutdownable);
    }

    @Override
    public void invoke(@NotNull Mode mode, @NotNull Runnable runnable) {
        super.invoke(mode, runnable);
    }

    public void invokeLimited(@NotNull Mode mode, @NotNull Runnable runnable) {
        invokeCheckedLimited(mode, () -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public <T> T invoke(@NotNull Mode mode, @NotNull Callable<T> task) throws Exception {
        return super.invoke(mode, task);
    }

    public <T> Future<T> invokeLimited(@NotNull Mode mode, @NotNull Callable<T> task) throws Exception {
        long delay = rateLimiter.reservePermission();
        if (delay == 0) {
            return CompletableFuture.completedFuture(invoke(mode, task));
        } else if (delay > 0) {
            return submitExecution(delay, mode, task, null);
        } else {
            throw new RateLimitException(false, "Rate limit exceeded for RateLimitInvoker " + rateLimiter.getName());
        }
    }

    @Override
    public <T> T invoke(@NotNull Mode mode, @NotNull Callable<T> task, @NotNull Function<Exception, RuntimeException> exceptionMapper) {
        return super.invoke(mode, task, exceptionMapper);
    }

    public <T> Future<T> invokeLimited(@NotNull Mode mode, @NotNull Callable<T> task, @NotNull Function<Exception, RuntimeException> exceptionMapper) {
        long delay = rateLimiter.reservePermission();
        if (delay == 0) {
            return CompletableFuture.completedFuture(invoke(mode, task, exceptionMapper));
        } else if (delay > 0) {
            return submitExecution(delay, mode, task, exceptionMapper);
        } else {
            throw new RateLimitException(false, "Rate limit exceeded for RateLimitInvoker " + rateLimiter.getName());
        }
    }

    @Override
    public <T> T invokeChecked(@NotNull Mode mode, @NotNull Callable<T> task) {
        return super.invokeChecked(mode, task);
    }

    public <T> Future<T> invokeCheckedLimited(@NotNull Mode mode, @NotNull Callable<T> task) {
        return invokeLimited(mode, task, RuntimeException::new);
    }

    private <T> Future<T> submitExecution(
        long delay,
        @NotNull Mode mode,
        @NotNull Callable<T> task,
        @Nullable Function<Exception, RuntimeException> exceptionMapper
    ) {
        ThreadContext forkedThreadContext = ThreadContext.Current.get().fork();
        return rateLimitQueueExecutor.schedule(() -> {
            ThreadContext.Current.installExplicitly(forkedThreadContext);
            try {
                if (exceptionMapper != null) {
                    return invoke(mode, task, exceptionMapper);
                } else {
                    return invoke(mode, task);
                }
            } catch (Exception e) {
                LOGGER.error("Error in rate limited task", e);
                throw e;
            } finally {
                forkedThreadContext.clear();
            }
        }, delay, TimeUnit.NANOSECONDS);
    }

}
