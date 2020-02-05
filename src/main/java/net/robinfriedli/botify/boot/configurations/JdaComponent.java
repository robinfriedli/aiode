package net.robinfriedli.botify.boot.configurations;

import java.util.Collections;

import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JdaComponent {

    @Value("${botify.tokens.discord_token}")
    private String discordToken;

    @Bean
    public ShardManager getShardManager() {
        try {
            return new DefaultShardManagerBuilder()
                .setToken(discordToken)
                .setHttpClientBuilder(new OkHttpClient.Builder().protocols(Collections.singletonList(Protocol.HTTP_1_1)))
                .setStatus(OnlineStatus.IDLE)
                .setChunkingFilter(ChunkingFilter.NONE)
                .build();
        } catch (LoginException e) {
            throw new RuntimeException("Failed to log in to discord", e);
        }
    }

}
