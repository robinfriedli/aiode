package net.robinfriedli.aiode.rest.endpoints;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.entities.ClientSession;
import net.robinfriedli.aiode.entities.GeneratedToken;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import net.robinfriedli.aiode.rest.SessionBean;
import net.robinfriedli.aiode.rest.exceptions.MissingAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionManagementEndpoint {

    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final QueryBuilderFactory queryBuilderFactory;
    private final ShardManager shardManager;

    public SessionManagementEndpoint(GuildManager guildManager, HibernateComponent hibernateComponent, QueryBuilderFactory queryBuilderFactory, ShardManager shardManager) {
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        this.queryBuilderFactory = queryBuilderFactory;
        this.shardManager = shardManager;
    }

    @PostMapping(path = {"/generate_token/", "/generate_token/{previous_token}"})
    public String generateToken(@PathVariable(name = "previous_token", required = false) String oldTokenStr, HttpServletRequest request, HttpServletResponse response) {
        String remoteAddr = request.getRemoteAddr();

        return hibernateComponent.invokeWithSession(session -> {
            UUID token;
            boolean isValid = false;

            if (oldTokenStr != null) {
                UUID oldToken = UUID.fromString(oldTokenStr);
                CriteriaBuilder deleteQueryCb = session.getCriteriaBuilder();
                CriteriaDelete<GeneratedToken> deleteQuery = deleteQueryCb.createCriteriaDelete(GeneratedToken.class);
                Root<GeneratedToken> deleteQueryRoot = deleteQuery.from(GeneratedToken.class);
                deleteQuery.where(deleteQueryCb.equal(deleteQueryRoot.get("token"), oldToken));

                session.createMutationQuery(deleteQuery).executeUpdate();
            }

            do {
                token = UUID.randomUUID();
                UUID finalToken = token;

                // the chance of the same token being generated twice at all, let alone concurrently, is low enough to avoid synchronisation
                Long existingTokens = queryBuilderFactory.select(GeneratedToken.class, (from, cb) -> cb.count(from.get("pk")), Long.class)
                    .where((cb, root) -> cb.equal(root.get("token"), finalToken))
                    .build(session)
                    .uniqueResult();
                Long existingClients = queryBuilderFactory.select(ClientSession.class, (from, cb) -> cb.count(from.get("pk")), Long.class)
                    .where((cb, root) -> cb.equal(root.get("sessionId"), finalToken))
                    .build(session)
                    .uniqueResult();

                if (existingTokens == 0 && existingClients == 0) {
                    isValid = true;

                    GeneratedToken generatedToken = new GeneratedToken();
                    generatedToken.setToken(finalToken);
                    generatedToken.setIp(remoteAddr);

                    session.persist(generatedToken);
                }
            } while (!isValid);

            setSessionCookie(response, token.toString());
            return token.toString();
        });
    }

    @GetMapping(path = "/fetch_session/{session_id}", produces = "application/json")
    public ResponseEntity<SessionBean> fetchSession(@PathVariable(name = "session_id") String sessionId, HttpServletRequest request, HttpServletResponse response) {
        UUID uuid = UUID.fromString(sessionId);
        String remoteAddr = request.getRemoteAddr();

        Optional<ClientSession> foundClientSession = hibernateComponent.invokeWithSession(session -> queryBuilderFactory.find(ClientSession.class)
            .where((cb, root) -> cb.and(
                cb.equal(root.get("sessionId"), uuid),
                cb.equal(root.get("ipAddress"), remoteAddr)
            ))
            .build(session)
            .uniqueResultOptional()
        );

        if (foundClientSession.isPresent()) {
            ClientSession clientSession = foundClientSession.get();
            long userId = clientSession.getUserId();
            long guildId = clientSession.getGuildId();
            long textChannelId = clientSession.getTextChannelId();

            Guild guild = shardManager.getGuildById(guildId);
            if (guild == null) {
                throw new MissingAccessException("Could not connect to guild, aiode is probably no longer a member.");
            }

            Member member = guild.getMemberById(userId);
            if (member == null) {
                throw new MissingAccessException("Could not connect to user, user is probably no longer a member.");
            }

            TextChannel textChannel = guild.getTextChannelById(textChannelId);
            if (textChannel == null) {
                textChannel = guildManager.getDefaultTextChannelForGuild(guild);
                TextChannel finalTextChannel = textChannel;
                hibernateComponent.consumeSession(session -> {
                    clientSession.setTextChannelId(finalTextChannel.getIdLong());
                    session.update(clientSession);
                });
            }

            setSessionCookie(response, sessionId);
            return ResponseEntity.ok(SessionBean.create(userId, member.getEffectiveName(), guildId, guild.getName(), textChannelId, textChannel.getName(), sessionId));
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping(path = "/session_exists/{session_id}")
    public boolean sessionExists(@PathVariable(name = "session_id") String sessionId, HttpServletRequest request, HttpServletResponse response) {
        UUID uuid = UUID.fromString(sessionId);
        String remoteAddr = request.getRemoteAddr();

        Long matchingSessionCount = hibernateComponent.invokeWithSession(session -> queryBuilderFactory.select(ClientSession.class, (from, cb) -> cb.count(from.get("pk")), Long.class)
            .where((cb, root) -> cb.and(
                cb.equal(root.get("sessionId"), uuid),
                cb.equal(root.get("ipAddress"), remoteAddr)
            ))
            .build(session)
            .uniqueResult());

        boolean sessionExists = matchingSessionCount > 0;

        if (sessionExists) {
            setSessionCookie(response, sessionId);
        }

        return sessionExists;
    }

    private void setSessionCookie(HttpServletResponse response, String sessionId) {
        Cookie sessionIdCookie = new Cookie("botify_session_id", sessionId);
        sessionIdCookie.setMaxAge(1800);
        // TODO secure
        response.addCookie(sessionIdCookie);
    }

}
