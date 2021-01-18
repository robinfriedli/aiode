package net.robinfriedli.botify.boot.configurations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import org.discordbots.api.client.DiscordBotListAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TopGGComponent {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Value("${botify.tokens.discord_bot_id}")
    private String discordBotId;
    @Value("${botify.tokens.topgg_token}")
    private String topGGToken;

    @Bean
    public DiscordBotListAPI getDiscordBotListAPI() {
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
        return discordBotListAPI;
    }

}
