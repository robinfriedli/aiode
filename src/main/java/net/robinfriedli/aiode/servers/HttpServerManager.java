package net.robinfriedli.aiode.servers;

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
import net.robinfriedli.aiode.boot.AbstractShutdownable;
import net.robinfriedli.aiode.entities.xml.HttpHandlerContribution;
import net.robinfriedli.aiode.exceptions.handler.handlers.LoggingUncaughtExceptionHandler;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Service that starts the {@link HttpServer} and creates a context for all {@link HttpHandler} defined in the
 * httpHandlers XML configuration
 */
@Component
public class HttpServerManager extends AbstractShutdownable {

    private final Context httpHandlersContext;
    @Value("${aiode.server.port}")
    private int port;
    private HttpServer httpServer;

    public HttpServerManager(@Value("classpath:xml-contributions/httpHandlers.xml") Resource commandResource, JxpBackend jxpBackend) {
        try {
            this.httpHandlersContext = jxpBackend.createContext(commandResource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Could not instantiate " + getClass().getSimpleName(), e);
        }
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);

        ExecutorService executorService = new ThreadPoolExecutor(5, 50, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadFactory() {
            final AtomicLong threadId = new AtomicLong(1);

            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("http-server-thread-" + threadId.getAndIncrement());
                thread.setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler());
                return thread;
            }
        });
        httpServer.setExecutor(executorService);

        for (HttpHandlerContribution contribution : httpHandlersContext.getInstancesOf(HttpHandlerContribution.class)) {
            httpServer.createContext(contribution.getAttribute("path").getValue(), contribution.instantiate());
        }

        httpServer.start();
    }

    @Override
    public void shutdown(int delay) {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

}
