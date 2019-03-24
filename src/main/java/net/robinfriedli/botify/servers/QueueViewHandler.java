package net.robinfriedli.botify.servers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.AudioQueue;
import net.robinfriedli.botify.audio.Playable;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.Util;

public class QueueViewHandler implements HttpHandler {

    private final JDA jda;
    private final AudioManager audioManager;

    public QueueViewHandler(JDA jda, AudioManager audioManager) {
        this.jda = jda;
        this.audioManager = audioManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String queuePagePath = PropertiesLoadingService.requireProperty("QUEUE_PAGE_PATH");
            String html = Files.readString(Path.of(queuePagePath));
            List<NameValuePair> parameters = URLEncodedUtils.parse(exchange.getRequestURI(), Charset.forName("UTF-8"));
            Map<String, String> parameterMap = new HashMap<>();
            parameters.forEach(param -> parameterMap.put(param.getName(), param.getValue()));
            String guildId = parameterMap.get("guildId");

            if (guildId != null) {
                Guild guild = jda.getGuildById(guildId);
                if (guild != null) {
                    AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                    AudioQueue queue = playback.getAudioQueue();
                    String content;
                    if (!queue.isEmpty()) {
                        int position = queue.getPosition();
                        List<Playable> previous = queue.listPrevious(position);
                        List<Playable> next = queue.listNext(queue.getTracks().size() - position);

                        StringBuilder listBuilder = new StringBuilder();
                        if (!previous.isEmpty()) {
                            if (previous.size() > 20) {
                                listBuilder.append("<a href=\"#current\">Jump to current track</a>").append(System.lineSeparator());
                            }
                            appendList(listBuilder, previous, "Previous");
                        }
                        listBuilder.append("<div id=\"current\">").append(System.lineSeparator());
                        appendList(listBuilder, Collections.singletonList(queue.getCurrent()), "Current");
                        listBuilder.append("</div>").append(System.lineSeparator());
                        if (!next.isEmpty()) {
                            appendList(listBuilder, next, "Next");
                        }

                        content = listBuilder.toString();
                    } else {
                        content = "Queue is empty";
                    }

                    String response = String.format(html,
                        boolToString(playback.isPaused()),
                        boolToString(playback.isShuffle()),
                        boolToString(playback.isRepeatAll()),
                        boolToString(playback.isRepeatOne()),
                        content);

                    byte[] bytes = response.getBytes();
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream outputStream = exchange.getResponseBody();
                    outputStream.write(bytes);
                    outputStream.close();
                } else {
                    throw new IllegalStateException("Guild " + guildId + " not found");
                }
            } else {
                throw new IllegalArgumentException("No guild provided");
            }
        } catch (Throwable e) {
            String loginPagePath = PropertiesLoadingService.requireProperty("ERROR_PAGE_PATH");
            String html = Files.readString(Path.of(loginPagePath));
            String response = String.format(html, e.getMessage());
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(response.getBytes());
            responseBody.close();
        }
    }

    private void appendList(StringBuilder listBuilder, List<Playable> playables, String title) {
        listBuilder.append("<h3>").append(title).append("</h3>").append(System.lineSeparator());
        listBuilder.append("<table class=\"content-table\">").append(System.lineSeparator());
        listBuilder.append("<tbody>").append(System.lineSeparator());
        for (Playable playable : playables) {
            listBuilder.append("<tr>").append(System.lineSeparator());
            listBuilder.append("<td>").append(playable.getDisplayInterruptible()).append("</td>").append(System.lineSeparator());
            listBuilder.append("<td>").append(Util.normalizeMillis(playable.getDurationMsInterruptible())).append("</td>").append(System.lineSeparator());
            listBuilder.append("</tr>").append(System.lineSeparator());
        }
        listBuilder.append("</tbody>").append(System.lineSeparator());
        listBuilder.append("</table>");
    }

    private String boolToString(boolean bool) {
        return bool ? "On" : "Off";
    }

}
