package net.robinfriedli.aiode.boot;

import java.util.concurrent.ExecutorService;

public class ShutdownableExecutorService implements Shutdownable {

    private final ExecutorService executorService;

    public ShutdownableExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void shutdown(int delayMs) {
        executorService.shutdown();
    }

}
