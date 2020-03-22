package net.robinfriedli.botify.boot.configurations;

import java.util.EnumSet;

import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static net.dv8tion.jda.api.requests.GatewayIntent.*;
import static net.dv8tion.jda.api.utils.cache.CacheFlag.*;

@Configuration
public class JdaComponent {

    @Value("${botify.tokens.discord_token}")
    private String discordToken;

    @Bean
    public ShardManager getShardManager() {
        // the gateway intent GUILD_MEMBERS normally required by the GuildMemberUpdateNicknameEvent (see GuildManagementListener)
        // is not needed since bots always receive member updates if the affected member is the bot itself which is all
        // this event is used for
        EnumSet<GatewayIntent> gatewayIntents = EnumSet.of(GUILD_MESSAGES, GUILD_MESSAGE_REACTIONS, DIRECT_MESSAGES, GUILD_VOICE_STATES);
        try {
            return DefaultShardManagerBuilder.create(discordToken, gatewayIntents)
                .setDisabledCacheFlags(EnumSet.of(ACTIVITY, EMOTE, CLIENT_STATUS))
                .setMemberCachePolicy(MemberCachePolicy.DEFAULT)
                .setStatus(OnlineStatus.IDLE)
                .setChunkingFilter(ChunkingFilter.NONE)
                .build();
        } catch (LoginException e) {
            throw new RuntimeException("Failed to log in to discord", e);
        }
    }

}
