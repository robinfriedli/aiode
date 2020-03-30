package net.robinfriedli.botify.servers;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.robinfriedli.botify.boot.AbstractShutdownable;
import net.robinfriedli.botify.entities.xml.HttpHandlerContribution;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
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
    @Value("${botify.server.port}")
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
        httpServer.setExecutor(null);

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
