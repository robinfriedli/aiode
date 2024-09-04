package net.robinfriedli.aiode.boot.tasks;


import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import net.robinfriedli.aiode.function.RateLimitInvoker;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import org.jetbrains.annotations.Nullable;

/**
 * If this is a private bot as defined via `aiode.preferences.private_instance_identifier`, leave guilds on startup where
 * the private instance identifier is not assigned to the {@link GuildSpecification}
 */
public class LeaveUnassignedPrivateBotTask implements StartupTask {

    private static final RateLimitInvoker RATE_LIMIT_INVOKER = new RateLimitInvoker("leave_unassigned_guilds", 50, Duration.ofSeconds(20));

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HibernateComponent hibernateComponent;
    private final QueryBuilderFactory queryBuilderFactory;
    private final StartupTaskContribution contribution;

    public LeaveUnassignedPrivateBotTask(HibernateComponent hibernateComponent, QueryBuilderFactory queryBuilderFactory, StartupTaskContribution contribution) {
        this.hibernateComponent = hibernateComponent;
        this.queryBuilderFactory = queryBuilderFactory;
        this.contribution = contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) throws Exception {
        String privateInstanceIdentifier = Aiode.get().getSpringPropertiesConfig().getApplicationProperty("aiode.preferences.private_instance_identifier");
        if (!Strings.isNullOrEmpty(privateInstanceIdentifier)) {
            hibernateComponent.consumeSession(session -> {
                List<Guild> guilds = Objects.requireNonNull(shard).getGuilds();
                List<String> unassignedGuildIds = queryBuilderFactory
                    .select(GuildSpecification.class, "guildId", String.class)
                    .where((cb, root) -> cb.and(
                        root.get("guildId").in(guilds.stream().map(ISnowflake::getId).toList()),
                        cb.or(
                            cb.isNull(root.get("privateBotInstanceId")),
                            cb.notEqual(root.get("privateBotInstanceId"), privateInstanceIdentifier)
                        )
                    ))
                    .build(session)
                    .getResultList();

                if (!unassignedGuildIds.isEmpty()) {
                    logger.info("Found {} guilds this private bot is no longer assigned to, going to leave guilds", unassignedGuildIds.size());
                    for (Guild guild : guilds) {
                        if (unassignedGuildIds.contains(guild.getId())) {
                            RATE_LIMIT_INVOKER.invokeLimited(() -> guild.leave().queue());
                        }
                    }
                }
            });
        }
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }
}
