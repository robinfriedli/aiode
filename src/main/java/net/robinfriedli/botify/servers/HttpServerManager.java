package net.robinfriedli.botify.servers;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.robinfriedli.botify.boot.AbstractShutdownable;
import net.robinfriedli.botify.entities.xml.HttpHandlerContribution;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.persist.Context;

/**
 * Service that starts the {@link HttpServer} and creates a context for all {@link HttpHandler} defined in the
 * httpHandlers XML configuration
 */
public class HttpServerManager extends AbstractShutdownable {

    private final Context httpHandlersContext;
    private HttpServer httpServer;

    public HttpServerManager(Context httpHandlersContext) {
        this.httpHandlersContext = httpHandlersContext;
    }

    public void start() throws IOException {
        int port = PropertiesLoadingService.requireProperty(Integer.class, "SERVER_PORT");
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
