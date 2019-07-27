package net.robinfriedli.botify.boot.tasks;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.jxp.queries.Conditions;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class VersionUpdateAlertTask implements StartupTask {

    private final JDA jda;
    private final JxpBackend jxpBackend;

    public VersionUpdateAlertTask(JDA jda, JxpBackend jxpBackend) {
        this.jda = jda;
        this.jxpBackend = jxpBackend;
    }

    @Override
    public void perform() throws Exception {
        Logger logger = LoggerFactory.getLogger(getClass());
        try (Context context = jxpBackend.getContext("./resources/versions.xml")) {
            BufferedReader bufferedReader = new BufferedReader(new FileReader("./resources/current-version.txt"));
            String currentVersion = bufferedReader.readLine();
            XmlElement versionElem = context.query(Conditions.attribute("version").is(currentVersion)).getOnlyResult();
            if (versionElem != null) {
                if (!versionElem.getAttribute("launched").getBool()) {
                    if (!(versionElem.hasAttribute("silent") && versionElem.getAttribute("silent").getBool())) {
                        sendUpdateAlert(context, currentVersion, versionElem);
                    }

                    context.invoke(() -> versionElem.setAttribute("launched", true));
                }
            } else {
                logger.warn("Current version has no version element in versions.xml");
            }
        }
    }

    private void sendUpdateAlert(Context context, String currentVersion, XmlElement versionElem) throws IOException {
        List<XmlElement> lowerLaunchedVersions = context.query(xmlElement -> xmlElement.getTagName().equals("version")
            && versionCompare(currentVersion, xmlElement.getAttribute("version").getValue()) == 1
            && xmlElement.getAttribute("launched").getBool()).collect();
        if (!lowerLaunchedVersions.isEmpty()) {
            MessageService messageService = new MessageService();
            String message = "Botify has been updated to " + currentVersion + ". [Check the releases here]("
                + "https://github.com/robinfriedli/botify/releases)";
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Update");
            embedBuilder.setDescription(message);

            List<XmlElement> features = versionElem.query(tagName("feature")).collect();
            if (!features.isEmpty()) {
                StringBuilder featuresBuilder = new StringBuilder();
                for (XmlElement feature : features) {
                    featuresBuilder.append("-\t").append(feature.getTextContent()).append(System.lineSeparator());
                }
                embedBuilder.addField("Features", featuresBuilder.toString(), false);
            }

            for (Guild guild : jda.getGuilds()) {
                messageService.sendWithLogo(embedBuilder, guild);
            }
        }
    }

    private int versionCompare(String s1, String s2) {
        String[] split1 = s1.split("\\.");
        String[] split2 = s2.split("\\.");

        for (int i = 0; i < split1.length; i++) {
            if (i > split2.length - 1) {
                return 1;
            }

            int v1 = Integer.parseInt(split1[i]);
            int v2 = Integer.parseInt(split2[i]);
            if (v1 > v2) {
                return 1;
            } else if (v1 < v2) {
                return -1;
            }
        }

        if (split1.length < split2.length) {
            return -1;
        }

        return 0;
    }

}
