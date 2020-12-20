package net.robinfriedli.botify.rest;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.concurrent.ThreadContext;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.ClientSession;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.rest.annotations.AuthenticationRequired;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestInterceptorHandler implements HandlerInterceptor {

    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final QueryBuilderFactory queryBuilderFactory;
    private final SecurityManager securityManager;
    private final ShardManager shardManager;
    private final SpotifyApi.Builder spotifyApiBuilder;

    public RequestInterceptorHandler(GuildManager guildManager, HibernateComponent hibernateComponent, QueryBuilderFactory queryBuilderFactory, SecurityManager securityManager, ShardManager shardManager, SpotifyApi.Builder spotifyApiBuilder) {
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        this.queryBuilderFactory = queryBuilderFactory;
        this.securityManager = securityManager;
        this.shardManager = shardManager;
        this.spotifyApiBuilder = spotifyApiBuilder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ThreadContext threadContext = ThreadContext.Current.get();
        threadContext.install(new RequestContext(request));

        try {
            Cookie[] cookies = request.getCookies();
            String sessionId = null;
            if (cookies != null) {
                sessionId = Arrays.stream(cookies)
                    .filter(c -> c.getName().equals("botify_session_id"))
                    .map(Cookie::getValue)
                    .findAny()
                    .orElse(null);
            }
            ClientSession clientSession = null;
            ExecutionContext executionContext = null;
            if (sessionId != null) {
                UUID uuid = UUID.fromString(sessionId);
                String remoteAddr = request.getRemoteAddr();

                Optional<ClientSession> existingClientSession = hibernateComponent.invokeWithSession(session ->
                    queryBuilderFactory.find(ClientSession.class)
                        .where((cb, root) -> cb.and(
                            cb.equal(root.get("sessionId"), uuid),
                            cb.equal(root.get("ipAddress"), remoteAddr)
                        ))
                        .build(session)
                        .uniqueResultOptional());

                if (existingClientSession.isPresent()) {
                    clientSession = existingClientSession.get();
                    executionContext = setupExecutionContext(clientSession);

                    if (executionContext == null) {
                        response.sendError(403, "Could not connect to guild or user. Either the bot is having connection issues or the bot is longer part of the connected guild or the connected member is no longer part of this guild.");
                        threadContext.clear();
                        return false;
                    }

                    ExecutionContext.Current.set(executionContext);
                }
            }

            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                AuthenticationRequired methodAnnotation = handlerMethod.getMethodAnnotation(AuthenticationRequired.class);
                if (methodAnnotation != null) {
                    if (clientSession == null) {
                        response.sendError(403, "This endpoint requires the client to be connected to an active Session");
                        threadContext.clear();
                        return false;
                    }

                    String[] requiredPermissions = methodAnnotation.requiredPermissions();
                    if (requiredPermissions.length > 0) {
                        Member member = executionContext.getMember();
                        boolean unauthorized = Arrays.stream(requiredPermissions)
                            .noneMatch(perm -> securityManager.askPermission(perm, member));

                        if (unauthorized) {
                            response.sendError(403, String.format("Member '%s' does not have any of the required permissions: %s", member.getEffectiveName(), String.join(", ", requiredPermissions)));
                            threadContext.clear();
                            return false;
                        }
                    }
                }
            }
        } catch (Exception e) {
            response.sendError(500, "Internal server error");
            threadContext.clear();
            throw e;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ThreadContext.Current.clear();
    }

    private ExecutionContext setupExecutionContext(ClientSession clientSession) {
        SessionFactory sessionFactory = hibernateComponent.getSessionFactory();

        Guild guild = shardManager.getGuildById(clientSession.getGuildId());
        if (guild == null) {
            return null;
        }

        Member member = guild.getMemberById(clientSession.getUserId());
        if (member == null) {
            return null;
        }

        TextChannel textChannel = guild.getTextChannelById(clientSession.getTextChannelId());
        if (textChannel == null) {
            textChannel = guildManager.getDefaultTextChannelForGuild(guild);
            TextChannel finalTextChannel = textChannel;
            hibernateComponent.consumeSession(session -> {
                clientSession.setTextChannelId(finalTextChannel.getIdLong());
                session.update(clientSession);
            });
        }

        JDA jda = guild.getJDA();
        GuildContext guildContext = guildManager.getContextForGuild(guild);
        return new ExecutionContext(guild, guildContext, jda, member, sessionFactory, spotifyApiBuilder, textChannel);
    }

}
