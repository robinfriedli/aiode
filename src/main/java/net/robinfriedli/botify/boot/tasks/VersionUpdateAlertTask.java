package net.robinfriedli.botify.boot.tasks;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.boot.VersionManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.xml.Version;
import net.robinfriedli.botify.function.CheckedConsumer;
import net.robinfriedli.botify.util.StaticSessionProvider;
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

    private final ShardManager shardManager;
    private final MessageService messageService;
    private final VersionManager versionManager;

    public VersionUpdateAlertTask(ShardManager shardManager, MessageService messageService, VersionManager versionManager) {
        this.shardManager = shardManager;
        this.messageService = messageService;
        this.versionManager = versionManager;
    }

    @Override
    public void perform() {
        Logger logger = LoggerFactory.getLogger(getClass());
        Version versionElem = versionManager.getCurrentVersion();
        if (versionElem != null) {
            Context context = versionManager.getContext();
            if (!versionElem.getAttribute("launched").getBool()) {
                if (!(versionElem.hasAttribute("silent") && versionElem.getAttribute("silent").getBool())) {
                    sendUpdateAlert(context, versionElem);
                }

                context.invoke(() -> versionElem.setAttribute("launched", true));
            }
        } else {
            logger.warn("Current version has no version element in versions.xml");
        }
    }

    private void sendUpdateAlert(Context context, Version versionElem) {
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

            // setup current thread session and handle all guilds within one session instead of opening a new session for each
            StaticSessionProvider.invokeWithSession((CheckedConsumer<Session>) session -> {
                for (Guild guild : shardManager.getGuilds()) {
                    messageService.sendWithLogo(embedBuilder, guild);
                }
            });
        }
    }

}
