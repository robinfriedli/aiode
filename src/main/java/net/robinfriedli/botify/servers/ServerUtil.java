package net.robinfriedli.botify.servers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import com.sun.net.httpserver.HttpExchange;

public class ServerUtil {

    public static void handleError(HttpExchange exchange, Throwable e) throws IOException {
        String html = Files.readString(Path.of("html/default_error_page.html"));
        String response = String.format(html, e.getMessage());
        exchange.sendResponseHeaders(200, response.getBytes().length);
        OutputStream responseBody = exchange.getResponseBody();
        responseBody.write(response.getBytes());
        responseBody.close();
    }

    public static Map<String, String> getParameters(HttpExchange exchange) {
        List<NameValuePair> parameters = URLEncodedUtils.parse(exchange.getRequestURI(), StandardCharsets.UTF_8);
        Map<String, String> parameterMap = new HashMap<>();
        parameters.forEach(param -> parameterMap.put(param.getName(), param.getValue()));
        return parameterMap;
    }

}
