package net.robinfriedli.aiode.cron.tasks;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.cron.AbstractCronTask;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.function.modes.HibernateTransactionMode;
import net.robinfriedli.exec.Mode;
import org.quartz.JobExecutionContext;

/**
 * For private bot instances, this checks whether the bot is still a member of guilds it is assigned to via {@link GuildSpecification#getPrivateBotInstance()}.
 * <p>
 * If the bot is still a member of that guild, this updates {@link GuildSpecification#setPrivateBotAssignmentLastHeartbeat(OffsetDateTime)} to the current timestamp,
 * else this removes the assignment if the last successful heartbeat is more than two hours ago. This is especially relevant if a user
 * assigns a private bot instance, but never actually ends up inviting it.
 */
public class PrivateBotAssignmentHeartbeatTask extends AbstractCronTask {

    @Override
    protected void run(JobExecutionContext jobExecutionContext) throws Exception {
        String privateInstanceIdentifier = Aiode.get().getSpringPropertiesConfig().getApplicationProperty("aiode.preferences.private_instance_identifier");
        if (!Strings.isNullOrEmpty(privateInstanceIdentifier)) {
            Set<String> guildIds = Aiode.get().getShardManager().getGuilds().stream().map(ISnowflake::getId).collect(Collectors.toSet());
            Aiode.get().getHibernateComponent().consumeSession(session -> {
                List<GuildSpecification> assignedGuildSpecifications = Aiode.get().getQueryBuilderFactory()
                    .find(GuildSpecification.class)
                    .where((cb, root) -> cb.equal(root.get("privateBotInstanceId"), privateInstanceIdentifier))
                    .build(session)
                    .getResultList();

                for (GuildSpecification assignedGuildSpecification : assignedGuildSpecifications) {
                    if (guildIds.contains(assignedGuildSpecification.getGuildId())) {
                        assignedGuildSpecification.setPrivateBotAssignmentLastHeartbeat(OffsetDateTime.now());
                    } else {
                        OffsetDateTime privateBotAssignmentLastHeartbeat = assignedGuildSpecification.getPrivateBotAssignmentLastHeartbeat();
                        OffsetDateTime now = OffsetDateTime.now();
                        if (privateBotAssignmentLastHeartbeat == null
                            || Duration.between(privateBotAssignmentLastHeartbeat, now).compareTo(Duration.ofHours(2)) > 0) {
                            assignedGuildSpecification.setPrivateBotInstance(null);
                            assignedGuildSpecification.setPrivateBotAssignmentLastHeartbeat(null);
                        }
                    }
                }
            });
        }
    }

    @Override
    protected Mode getMode() {
        return Mode.create().with(new HibernateTransactionMode());
    }
}
