package net.robinfriedli.botify.boot.configurations;

import java.util.EnumSet;

import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.robinfriedli.botify.discord.listeners.StartupListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import static net.dv8tion.jda.api.requests.GatewayIntent.*;
import static net.dv8tion.jda.api.utils.cache.CacheFlag.*;

@Configuration
// make sure JDA is set up after liquibase so schema changes are applied before the StartupListener runs
@DependsOn("liquibase")
public class JdaComponent {

    @Value("${botify.tokens.discord_token}")
    private String discordToken;

    private final StartupListener startupListener;

    public JdaComponent(StartupListener startupListener) {
        this.startupListener = startupListener;
    }

    @Bean
    public ShardManager getShardManager() {
        // the gateway intent GUILD_MEMBERS normally required by the GuildMemberUpdateNicknameEvent (see GuildManagementListener)
        // is not needed since bots always receive member updates if the affected member is the bot itself which is all
        // this event is used for
        EnumSet<GatewayIntent> gatewayIntents = EnumSet.of(GUILD_MESSAGES, GUILD_MESSAGE_REACTIONS, DIRECT_MESSAGES, GUILD_VOICE_STATES);
        try {
            return DefaultShardManagerBuilder.create(discordToken, gatewayIntents)
                .disableCache(EnumSet.of(ACTIVITY, EMOTE, CLIENT_STATUS))
                .setMemberCachePolicy(MemberCachePolicy.DEFAULT)
                .setStatus(OnlineStatus.IDLE)
                .setChunkingFilter(ChunkingFilter.NONE)
                .addEventListeners(startupListener)
                .build();
        } catch (LoginException e) {
            throw new RuntimeException("Failed to log in to discord", e);
        }
    }

}
