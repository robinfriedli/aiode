package net.robinfriedli.botify.servers;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.robinfriedli.botify.entities.xml.HttpHandlerContribution;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.persist.Context;

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
        int port = Integer.parseInt(PropertiesLoadingService.requireProperty("SERVER_PORT"));
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(null);

        for (HttpHandlerContribution contribution : httpHandlersContext.getInstancesOf(HttpHandlerContribution.class)) {
            httpServer.createContext(contribution.getAttribute("path").getValue(), contribution.instantiate());
        }

        httpServer.start();
    }

}
