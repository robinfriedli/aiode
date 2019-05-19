package net.robinfriedli.botify.boot.tasks;

import java.util.List;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.core.JDA;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.jxp.queries.Query;
import org.hibernate.Session;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Task that ensures that a accessConfiguration exists for the permission command for all guilds
 */
public class CreatePermissionAccessConfigurationTask implements StartupTask {

    @Override
    public void perform(JxpBackend jxpBackend, JDA jda, SpotifyApi spotifyApi, YouTubeService youTubeService, Session session) {
        String guildSpecificationPath = PropertiesLoadingService.requireProperty("GUILD_SPECIFICATION_PATH");
        Context context = jxpBackend.getContext(guildSpecificationPath);
        List<GuildSpecification> guildSpecifications = context.getInstancesOf(GuildSpecification.class);
        context.invoke(() -> {
            for (GuildSpecification guildSpecification : guildSpecifications) {
                XmlElement existingConfiguration = Query.evaluate(and(
                    instanceOf(AccessConfiguration.class),
                    attribute("commandIdentifier").fuzzyIs("permission")
                )).execute(guildSpecification.getSubElements()).getOnlyResult();

                if (existingConfiguration == null) {
                    AccessConfiguration accessConfiguration = new AccessConfiguration("permission", Lists.newArrayList(), context);
                    guildSpecification.addSubElement(accessConfiguration);
                }
            }
        });
    }
}
