package net.robinfriedli.botify.servers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.robinfriedli.botify.entities.xml.HttpHandlerContribution;
import net.robinfriedli.botify.exceptions.handlers.LoggingExceptionHandler;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.persist.Context;
import org.jetbrains.annotations.NotNull;

/**
 * Service that starts the {@link HttpServer} and creates a context for all {@link HttpHandler} defined in the
 * httpHandlers XML configuration
 */
public class HttpServerStarter {

    private final Context httpHandlersContext;

    public HttpServerStarter(Context httpHandlersContext) {
        this.httpHandlersContext = httpHandlersContext;
    }

    public void start() throws IOException {
        int port = PropertiesLoadingService.requireProperty(Integer.class, "SERVER_PORT");
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);

        ExecutorService executorService = new ThreadPoolExecutor(1, 5, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadFactory() {
            AtomicLong threadId = new AtomicLong(1);

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("http-server-thread-" + threadId.getAndIncrement());
                thread.setUncaughtExceptionHandler(new LoggingExceptionHandler());
                return thread;
            }
        });
        httpServer.setExecutor(executorService);

        for (HttpHandlerContribution contribution : httpHandlersContext.getInstancesOf(HttpHandlerContribution.class)) {
            httpServer.createContext(contribution.getAttribute("path").getValue(), contribution.instantiate());
        }

        httpServer.start();
    }

}
