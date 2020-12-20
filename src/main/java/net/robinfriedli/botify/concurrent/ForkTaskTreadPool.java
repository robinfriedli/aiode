package net.robinfriedli.botify.concurrent;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import net.robinfriedli.threadpool.ThreadPool;
import org.jetbrains.annotations.NotNull;

public class ForkTaskTreadPool extends AbstractExecutorService {

    private final ThreadPool threadPool;

    public ForkTaskTreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    @Override
    public void execute(Runnable command) {
        ThreadContext forkedThreadContext = ThreadContext.Current.get().fork();
        threadPool.execute(() -> {
            ThreadContext.Current.installExplicitly(forkedThreadContext);
            command.run();
        });
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    @Override
    public void shutdown() {
        threadPool.shutdown();
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
        return threadPool.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return threadPool.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return threadPool.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return threadPool.awaitTermination(timeout, unit);
    }
}
