package net.robinfriedli.botify.boot.tasks;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.boot.VersionManager;
import net.robinfriedli.botify.concurrent.LoggingThreadFactory;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.xml.StartupTaskContribution;
import net.robinfriedli.botify.entities.xml.Version;
import net.robinfriedli.botify.function.CheckedConsumer;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;

import static net.robinfriedli.botify.boot.VersionManager.Conditions.*;
import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Checks if the current version has been launched before and, if not and if the silent attribute is not set or false,
 * sends an update notification with the new features to each guild and then updates the launched attribute
 */
public class VersionUpdateAlertTask implements StartupTask {

    private static final Object DISPATCH_LOCK = new Object();
    private static final int MESSAGES_PER_SECOND = 3;
    private static final ScheduledExecutorService MESSAGE_DISPATCH = Executors.newScheduledThreadPool(
        0,
        new LoggingThreadFactory("version-update-dispatch")
    );
    // since the task is run for each shard separately and the launched flag gets set to true the first time,
    // this flag is used to remember whether there has been an update
    private static boolean UPDATED = false;
    private static int OFFSET = 0;
    private final MessageService messageService;
    private final StartupTaskContribution contribution;
    private final VersionManager versionManager;

    public VersionUpdateAlertTask(MessageService messageService, StartupTaskContribution contribution, VersionManager versionManager) {
        this.messageService = messageService;
        this.contribution = contribution;
        this.versionManager = versionManager;
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) {
        Logger logger = LoggerFactory.getLogger(getClass());
        Version versionElem = versionManager.getCurrentVersion();
        if (versionElem != null) {
            Context context = versionManager.getContext();
            if (UPDATED || !versionElem.getAttribute("launched").getBool()) {
                UPDATED = true;
                if (!(versionElem.hasAttribute("silent") && versionElem.getAttribute("silent").getBool())) {
                    sendUpdateAlert(context, versionElem, shard);
                }

                context.invoke(() -> versionElem.setAttribute("launched", true));
            }
        } else {
            logger.warn("Current version has no version element in versions.xml");
        }
    }

    private void sendUpdateAlert(Context context, Version versionElem, JDA shard) {
        List<XmlElement> lowerLaunchedVersions = context.query(and(
            tagName("version"),
            attribute("launched").is(true),
            lowerVersionThan(versionElem.getVersion())
        )).collect();
        if (!lowerLaunchedVersions.isEmpty()) {
            String message = "Botify has been updated to " + versionElem.getVersion() + ". [Check the releases here]("
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
            long delaySecs = OFFSET++ * (guilds.size() / MESSAGES_PER_SECOND);
            if (delaySecs > 0) {
                delaySecs += 10;
            }

            MESSAGE_DISPATCH.schedule(() -> {
                // use a lock in case one shard has fewer guilds and thus did not calculate enough of a delay to align
                // with the other shards
                synchronized (DISPATCH_LOCK) {
                    // setup current thread session and handle all guilds within one session instead of opening a new session for each
                    StaticSessionProvider.consumeSession((CheckedConsumer<Session>) session -> {
                        int counter = 0;
                        long currentTimeMillis = System.currentTimeMillis();
                        for (Guild guild : guilds) {
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
                }
            }, delaySecs, TimeUnit.SECONDS);

        }
    }

}
