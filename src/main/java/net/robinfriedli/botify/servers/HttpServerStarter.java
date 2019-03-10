package net.robinfriedli.botify.servers;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import net.robinfriedli.botify.entities.HttpHandlerContribution;
import net.robinfriedli.botify.util.ParameterContainer;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.persist.Context;

public class HttpServerStarter {

    private final Context httpHandlersContext;
    private final ParameterContainer parameterContainer;

    public HttpServerStarter(Context httpHandlersContext, ParameterContainer parameterContainer) {
        this.httpHandlersContext = httpHandlersContext;
        this.parameterContainer = parameterContainer;
    }

    public void start() throws IOException {
        int port = Integer.parseInt(PropertiesLoadingService.requireProperty("SERVER_PORT"));
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(null);

        for (HttpHandlerContribution contribution : httpHandlersContext.getInstancesOf(HttpHandlerContribution.class)) {
            httpServer.createContext(contribution.getAttribute("path").getValue(), contribution.instantiate(parameterContainer));
        }

        httpServer.start();
    }

}
