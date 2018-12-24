package net.robinfriedli.botify.discord;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Strings;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class GuildSpecificationManager {

    private final Context specificationContext;
    private final Map<Guild, GuildSpecification> specifiedGuilds = new HashMap<>();

    public GuildSpecificationManager(Context specificationContext) {
        this.specificationContext = specificationContext;
    }

    public void addGuild(Guild guild) {
        initializeGuild(guild);
    }

    public String getNameForGuild(Guild guild) {
        return specifiedGuilds.get(guild).getName();
    }

    public void setName(Guild guild, String name) {
        GuildSpecification guildSpecification = specifiedGuilds.get(guild);
        specificationContext.invoke(() -> guildSpecification.setAttribute("botifyName", name));
        guild.getController().setNickname(guild.getSelfMember(), name).queue();
    }

    private void initializeGuild(Guild guild) {
        XmlElement existingSpecification = specificationContext.query(
            and(
                instanceOf(GuildSpecification.class),
                attribute("guildId").is(guild.getId())
            )
        ).getOnlyResult();

        if (existingSpecification != null) {
            if (Strings.isNullOrEmpty(existingSpecification.getAttribute("botifyName").getValue())) {
                alertNameChange(guild);
            }

            specifiedGuilds.put(guild, (GuildSpecification) existingSpecification);
        } else {
            GuildSpecification newSpecification = specificationContext.invoke(() -> {
                GuildSpecification guildSpecification = new GuildSpecification(guild, specificationContext);
                guildSpecification.persist();
                return guildSpecification;
            });

            specifiedGuilds.put(guild, newSpecification);

            alertNameChange(guild);
        }
    }

    private void alertNameChange(Guild guild) {
        TextChannel defaultChannel = guild.getDefaultChannel();
        if (defaultChannel != null) {
            defaultChannel.sendMessage("Give me a name! Type \"$botify rename Your Name\"").queue();
        } else {
            TextChannel systemChannel = guild.getSystemChannel();
            if (systemChannel != null) {
                systemChannel.sendMessage("Give me a name! Type \"$botify rename Your Name\"").queue();
            }
        }
    }

}
