package net.robinfriedli.aiode.servers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler responsible for loading binaries in the resources-public directory
 */
public class ResourceHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(Path.of("." + exchange.getRequestURI().toString()));
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(bytes);
            responseBody.close();
        } catch (Exception e) {
            String response = e.toString();
            exchange.sendResponseHeaders(500, response.getBytes().length);
            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(response.getBytes());
            responseBody.close();
            LoggerFactory.getLogger(getClass()).error("Error in HttpHandler", e);
        }
    }
}
