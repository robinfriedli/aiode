package net.robinfriedli.aiode.servers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.Playable;
import net.robinfriedli.aiode.audio.queue.AudioQueue;
import net.robinfriedli.aiode.exceptions.InvalidRequestException;
import net.robinfriedli.aiode.util.Util;

public class QueueViewHandler implements HttpHandler {

    private final ShardManager shardManager;
    private final AudioManager audioManager;

    public QueueViewHandler(ShardManager shardManager, AudioManager audioManager) {
        this.shardManager = shardManager;
        this.audioManager = audioManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String html = Files.readString(Path.of("html/queue_view.html"));
            Map<String, String> parameterMap = ServerUtil.getParameters(exchange);
            String guildId = parameterMap.get("guildId");

            if (guildId != null) {
                Guild guild = shardManager.getGuildById(guildId);
                if (guild != null) {
                    AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
                    AudioQueue queue = playback.getAudioQueue();
                    String content;
                    if (!queue.isEmpty()) {
                        Lock readLock = queue.getLock().readLock();
                        readLock.lock();
                        try {
                            int position = queue.getPosition();
                            List<Playable> previous = queue.listPrevLocked(position);
                            List<Playable> next = queue.listNextLocked(queue.getTracks().size() - position);

                            StringBuilder listBuilder = new StringBuilder();
                            if (!previous.isEmpty()) {
                                if (previous.size() > 20) {
                                    listBuilder.append("<a href=\"#current\">Jump to current track</a>").append(System.lineSeparator());
                                }
                                appendList(listBuilder, previous, "Previous");
                            }
                            listBuilder.append("<div id=\"current\">").append(System.lineSeparator());
                            appendList(listBuilder, Collections.singletonList(queue.getCurrentLocked()), "Current");
                            listBuilder.append("</div>").append(System.lineSeparator());
                            if (!next.isEmpty()) {
                                appendList(listBuilder, next, "Next");
                            }

                            content = listBuilder.toString();
                        } finally {
                            readLock.unlock();
                        }
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
                    throw new InvalidRequestException("Guild " + guildId + " not found");
                }
            } else {
                throw new InvalidRequestException("No guild provided");
            }
        } catch (InvalidRequestException e) {
            ServerUtil.handleError(exchange, e);
        } catch (Exception e) {
            ServerUtil.handleError(exchange, e);
            LoggerFactory.getLogger(getClass()).error("Error in HttpHandler", e);
        }
    }

    private void appendList(StringBuilder listBuilder, List<Playable> playables, String title) {
        listBuilder.append("<h3>").append(title).append("</h3>").append(System.lineSeparator());
        listBuilder.append("<table class=\"content-table\">").append(System.lineSeparator());
        listBuilder.append("<tbody>").append(System.lineSeparator());
        for (Playable playable : playables) {
            listBuilder.append("<tr>").append(System.lineSeparator());
            listBuilder.append("<td>").append(playable.getDisplayNow()).append("</td>").append(System.lineSeparator());
            listBuilder.append("<td>").append(Util.normalizeMillis(playable.getDurationNow())).append("</td>").append(System.lineSeparator());
            listBuilder.append("</tr>").append(System.lineSeparator());
        }
        listBuilder.append("</tbody>").append(System.lineSeparator());
        listBuilder.append("</table>");
    }

    private String boolToString(boolean bool) {
        return bool ? "On" : "Off";
    }

}
