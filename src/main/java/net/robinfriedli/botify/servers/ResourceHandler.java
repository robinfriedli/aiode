package net.robinfriedli.botify.servers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler responsible for loading binaries in the resources-public directory
 */
public class ResourceHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("." + exchange.getRequestURI().toString()));
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream responseBody = exchange.getResponseBody();
        responseBody.write(bytes);
        responseBody.close();
    }
}
