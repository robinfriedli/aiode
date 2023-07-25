package net.robinfriedli.aiode.boot.configurations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.JDA;
import org.discordbots.api.client.DiscordBotListAPI;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TopGGComponent {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String discordBotId;
    private final String topGGToken;

    @Nullable
    private final DiscordBotListAPI discordBotListAPI;

    public TopGGComponent(@Value("${aiode.tokens.discord_bot_id}") String discordBotId, @Value("${aiode.tokens.topgg_token}") String topGGToken) {
        DiscordBotListAPI discordBotListAPI;
        if (!Strings.isNullOrEmpty(discordBotId) && !Strings.isNullOrEmpty(topGGToken)) {
            discordBotListAPI = new DiscordBotListAPI.Builder()
                .botId(discordBotId)
                .token(topGGToken)
                .build();
        } else {
            logger.info("top.gg api not set up, missing properties");
            discordBotListAPI = null;
        }
        this.discordBotListAPI = discordBotListAPI;
        this.discordBotId = discordBotId;
        this.topGGToken = topGGToken;
    }

    @Nullable
    @Bean
    public DiscordBotListAPI getDiscordBotListAPI() {
        return discordBotListAPI;
    }

    public void updateStatsForShard(JDA shard) {
        if (discordBotListAPI != null) {
            try {
                JDA.ShardInfo shardInfo = shard.getShardInfo();
                discordBotListAPI.setStats(shardInfo.getShardId(), shardInfo.getShardTotal(), (int) shard.getGuildCache().size())
                    .whenComplete(((unused, throwable) -> {
                        if (throwable != null) {
                            logger.error("Exception setting discordBotListAPI stats", throwable);
                        } else {
                            logger.info("Updated top.gg stats for shard " + shard);
                        }
                    }));
            } catch (Exception e) {
                logger.error("Exception setting discordBotListAPI stats", e);
            }
        }
    }

    public String getDiscordBotId() {
        return discordBotId;
    }

    public String getTopGGToken() {
        return topGGToken;
    }

}
