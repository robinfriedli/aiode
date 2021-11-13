package net.robinfriedli.botify.rest;

import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import se.michaelthelin.spotify.SpotifyApi;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final QueryBuilderFactory queryBuilderFactory;
    private final SecurityManager securityManager;
    private final ShardManager shardManager;
    private final SpotifyApi.Builder spotifyApiBuilder;

    public WebConfig(GuildManager guildManager, HibernateComponent hibernateComponent, QueryBuilderFactory queryBuilderFactory, SecurityManager securityManager, ShardManager shardManager, SpotifyApi.Builder spotifyApiBuilder) {
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        this.queryBuilderFactory = queryBuilderFactory;
        this.securityManager = securityManager;
        this.shardManager = shardManager;
        this.spotifyApiBuilder = spotifyApiBuilder;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestInterceptorHandler(guildManager, hibernateComponent, queryBuilderFactory, securityManager, shardManager, spotifyApiBuilder));
    }

}
