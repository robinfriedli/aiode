package net.robinfriedli.aiode.command.widget.widgets;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.command.widget.DynamicEmbedTablePaginationWidget;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.util.Util;

public class PlaylistPaginationWidget extends DynamicEmbedTablePaginationWidget<PlaylistItem> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Playlist playlist;

    public PlaylistPaginationWidget(
        WidgetRegistry widgetRegistry,
        Guild guild,
        MessageChannel channel,
        Playlist playlist
    ) {
        super(
            widgetRegistry,
            guild,
            channel,
            playlist.getName(),
            null,
            new Column[]{
                new Column<PlaylistItem>("Track", playlistItem -> {
                    String display = playlistItem.display();
                    return display.length() > 50
                        ? display.substring(0, 50) + "..."
                        : display;
                }),
                new Column<PlaylistItem>("Duration", playlistItem -> Util.normalizeMillis(playlistItem.getDuration()))
            },
            playlist.getItemsSorted()
        );
        this.playlist = playlist;
    }

    @Override
    protected EmbedBuilder prepareEmbedBuilder() {
        String createdUserId = playlist.getCreatedUserId();
        String createdUser;
        if (createdUserId.equals("system")) {
            createdUser = playlist.getCreatedUser();
        } else {
            ShardManager shardManager = Aiode.get().getShardManager();
            User userById;
            try {
                userById = shardManager.retrieveUserById(createdUserId).complete();
            } catch (Exception e) {
                if (!(e instanceof ErrorResponseException errorResponseException && errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER)) {
                    logger.error(String.format("Failed to load createdUser of playlist %d with user id %s", playlist.getPk(), createdUserId), e);
                }
                userById = null;
            }
            createdUser = userById != null ? userById.getName() : playlist.getCreatedUser();
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Duration", Util.normalizeMillis(playlist.getDuration()), true);
        embedBuilder.addField("Created by", createdUser, true);
        embedBuilder.addField("Tracks", String.valueOf(playlist.getSize()), true);

        SpringPropertiesConfig springPropertiesConfig = Aiode.get().getSpringPropertiesConfig();
        String baseUri = springPropertiesConfig.requireApplicationProperty("aiode.server.base_uri");
        String url = baseUri +
            String.format("/list?name=%s&guildId=%s", URLEncoder.encode(playlist.getName(), StandardCharsets.UTF_8), playlist.getGuildId());
        embedBuilder.addField("Full list", "[view online](" + url + ")", false);

        embedBuilder.addBlankField(false);

        embedBuilder.setThumbnail(baseUri + "/resources-public/img/aiode-logo.png");

        return embedBuilder;
    }
}
