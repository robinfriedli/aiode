package net.robinfriedli.botify.servers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.dv8tion.jda.core.JDA;
import net.robinfriedli.botify.discord.DiscordListener;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.Util;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class PlaylistViewHandler implements HttpHandler {

    private final JDA jda;
    private final SessionFactory sessionFactory;
    private final DiscordListener discordListener;

    public PlaylistViewHandler(JDA jda, SessionFactory sessionFactory, DiscordListener discordListener) {
        this.jda = jda;
        this.sessionFactory = sessionFactory;
        this.discordListener = discordListener;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Session session = null;
        try {
            String listPagePath = PropertiesLoadingService.requireProperty("LIST_PAGE_PATH");
            String html = Files.readString(Path.of(listPagePath));
            List<NameValuePair> parameters = URLEncodedUtils.parse(exchange.getRequestURI(), Charset.forName("UTF-8"));
            Map<String, String> parameterMap = new HashMap<>();
            parameters.forEach(param -> parameterMap.put(param.getName(), param.getValue()));
            String guildId = parameterMap.get("guildId");
            String name = parameterMap.get("name");

            boolean isPartitioned = discordListener.getMode() == DiscordListener.Mode.PARTITIONED;
            if (name != null && (guildId != null || !isPartitioned)) {
                session = sessionFactory.openSession();
                Playlist playlist = SearchEngine.searchLocalList(session, name, isPartitioned, guildId);
                if (playlist != null) {
                    String createdUserId = playlist.getCreatedUserId();
                    String createdUser;
                    if (createdUserId.equals("system")) {
                        createdUser = playlist.getCreatedUser();
                    } else {
                        createdUser = jda.getUserById(createdUserId).getName();
                    }
                    String htmlString = String.format(html,
                        playlist.getName(),
                        playlist.getName(),
                        Util.normalizeMillis(playlist.getDuration()),
                        createdUser,
                        playlist.getSize(),
                        getList(playlist));

                    byte[] bytes = htmlString.getBytes();
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                } else {
                    throw new IllegalStateException("No playlist found");
                }
            } else {
                throw new IllegalArgumentException("Insufficient request parameters");
            }
        } catch (Throwable e) {
            String loginPagePath = PropertiesLoadingService.requireProperty("ERROR_PAGE_PATH");
            String html = Files.readString(Path.of(loginPagePath));
            String response = String.format(html, e.getMessage());
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(response.getBytes());
            responseBody.close();
            LoggerFactory.getLogger(getClass()).error("Error in HttpHandler", e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private String getList(Playlist playlist) {
        StringBuilder listBuilder = new StringBuilder();
        List<PlaylistItem> playlistItems = playlist.getItemsSorted();
        for (int i = 0; i < playlistItems.size(); i++) {
            PlaylistItem item = playlistItems.get(i);
            listBuilder.append("<tr>").append(System.lineSeparator())
                .append("<td>").append(i + 1).append("</td>").append(System.lineSeparator())
                .append("<td>").append(item.display()).append("</td>").append(System.lineSeparator())
                .append("<td>").append(Util.normalizeMillis(item.getDuration())).append("</td>").append(System.lineSeparator())
                .append("</tr>").append(System.lineSeparator());
        }

        return listBuilder.toString();
    }

}
