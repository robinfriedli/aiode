package net.robinfriedli.aiode.boot.tasks;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.aiode.boot.VersionManager;
import net.robinfriedli.aiode.concurrent.LoggingThreadFactory;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import net.robinfriedli.aiode.entities.xml.Version;
import net.robinfriedli.aiode.function.CheckedConsumer;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import net.robinfriedli.jxp.api.XmlElement;
import org.hibernate.Session;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Checks if the current version has been launched before and, if not and if the silent attribute is not set or false,
 * sends an update notification with the new features to each guild and then updates the launched attribute
 */
public class VersionUpdateAlertTask implements StartupTask {

    private static final Object DISPATCH_LOCK = new Object();
    private static final int MESSAGES_PER_SECOND = 2;
    private static final ScheduledExecutorService MESSAGE_DISPATCH = Executors.newScheduledThreadPool(
        0,
        new LoggingThreadFactory("version-update-dispatch")
    );
    private static int OFFSET = 0;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final GuildManager guildManager;
    private final MessageService messageService;
    private final QueryBuilderFactory queryBuilderFactory;
    private final StartupTaskContribution contribution;
    private final VersionManager versionManager;

    public VersionUpdateAlertTask(GuildManager guildManager, MessageService messageService, QueryBuilderFactory queryBuilderFactory, StartupTaskContribution contribution, VersionManager versionManager) {
        this.guildManager = guildManager;
        this.messageService = messageService;
        this.queryBuilderFactory = queryBuilderFactory;
        this.contribution = contribution;
        this.versionManager = versionManager;
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) {
        Version versionElem = versionManager.getCurrentVersion();
        if (versionElem != null) {
            if (!versionElem.isSilentUpdate()) {
                sendUpdateAlert(versionElem, shard);
            }
        } else {
            logger.warn("Current version has no version element in versions.xml");
        }
    }

    private void sendUpdateAlert(Version versionElem, JDA shard) {
        String version = versionElem.getVersion();
        String message = "Aiode has been updated to " + version + ". [Check the releases here]("
            + "https://github.com/robinfriedli/botify/releases)";
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Update");
        embedBuilder.setDescription(message);

        List<XmlElement> features = versionElem.query(tagName("feature")).collect();
        if (!features.isEmpty()) {
            embedBuilder.addField("**Features**", "Changes in this update", false);
            for (XmlElement feature : features) {
                embedBuilder.addField(feature.getAttribute("title").getValue(), feature.getTextContent(), false);
            }
        }

        List<Guild> guilds = shard.getGuilds();

        Long guildsToUpdate = StaticSessionProvider.invokeWithSession(session -> queryBuilderFactory
            .count(GuildSpecification.class)
            .where((cb, root) -> cb.and(
                cb.or(
                    cb.notEqual(root.get("versionUpdateAlertSent"), version),
                    cb.isNull(root.get("versionUpdateAlertSent"))
                ),
                root.get("guildId").in(guilds.stream().map(Guild::getId).collect(Collectors.toSet()))
            ))
            .build(session)
            .uniqueResult()
        );

        if (guildsToUpdate == 0) {
            return;
        }

        int delaySecs = OFFSET++ * (guilds.size() / MESSAGES_PER_SECOND);
        if (delaySecs > 0) {
            delaySecs += 10;
        }

        logger.info("Scheduling dispatch of version update notification for {} guilds after a delay of {} seconds", guilds.size(), delaySecs);

        MESSAGE_DISPATCH.schedule(() -> {
            // use a lock in case one shard has fewer guilds and thus did not calculate enough of a delay to align
            // with the other shards
            synchronized (DISPATCH_LOCK) {
                try {
                    // setup current thread session and handle all guilds within one session instead of opening a new session for each
                    StaticSessionProvider.consumeSession((CheckedConsumer<Session>) session -> {
                        int counter = 0;
                        long currentTimeMillis = System.currentTimeMillis();
                        for (Guild guild : guilds) {
                            GuildContext guildContext = guildManager.getContextForGuild(guild);
                            GuildSpecification guildSpecification = guildContext.getSpecification(session);
                            if (Objects.equals(guildSpecification.getVersionUpdateAlertSent(), version)) {
                                continue;
                            } else {
                                guildSpecification.setVersionUpdateAlertSent(version);
                            }

                            messageService.sendWithLogo(embedBuilder, guild);

                            if (++counter % MESSAGES_PER_SECOND == 0) {
                                long delta = System.currentTimeMillis() - currentTimeMillis;
                                if (delta < 1000) {
                                    Thread.sleep(1000 - delta);
                                }
                                currentTimeMillis = System.currentTimeMillis();
                            }
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error sending version update alert", e);
                }
            }
        }, delaySecs, TimeUnit.SECONDS);
    }

}
