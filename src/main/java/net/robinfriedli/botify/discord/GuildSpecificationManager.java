package net.robinfriedli.botify.discord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.jxp.queries.Query;

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

    public boolean setName(Guild guild, String name) {
        GuildSpecification guildSpecification = specifiedGuilds.get(guild);
        specificationContext.invoke(() -> guildSpecification.setAttribute("botifyName", name));
        try {
            guild.getController().setNickname(guild.getSelfMember(), name).queue();
            return true;
        } catch (InsufficientPermissionException ignored) {
            return false;
        }
    }

    public boolean checkAccess(String commandIdentifier, Member member) {
        AccessConfiguration accessConfiguration = getAccessConfiguration(commandIdentifier, member.getGuild());
        return accessConfiguration == null || member.isOwner() || accessConfiguration.canAccess(member);
    }

    @Nullable
    public AccessConfiguration getAccessConfiguration(String commandIdentifier, Guild guild) {
        GuildSpecification guildSpecification = specifiedGuilds.get(guild);
        return (AccessConfiguration) Query.evaluate(and(
            attribute("commandIdentifier").is(commandIdentifier),
            instanceOf(AccessConfiguration.class)
        )).execute(guildSpecification.getSubElements()).getOnlyResult();
    }

    public void registerAccessConfiguration(String commandIdentifier, List<Role> roles, Guild guild) {
        GuildSpecification guildSpecification = specifiedGuilds.get(guild);
        AccessConfiguration existingAccessConfiguration = getAccessConfiguration(commandIdentifier, guild);

        if (existingAccessConfiguration != null) {
            throw new IllegalStateException("Access configuration for command " + commandIdentifier + " already exists.");
        }

        AccessConfiguration accessConfiguration = new AccessConfiguration(commandIdentifier, roles, specificationContext);
        specificationContext.invoke(() -> guildSpecification.addSubElement(accessConfiguration));
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
                AccessConfiguration permissionConfiguration = new AccessConfiguration("permission", Lists.newArrayList(), specificationContext);
                guildSpecification.addSubElement(permissionConfiguration);
                guildSpecification.persist();
                return guildSpecification;
            });

            specifiedGuilds.put(guild, newSpecification);

            alertNameChange(guild);
        }
    }

    private void alertNameChange(Guild guild) {
        try {
            TextChannel defaultChannel = guild.getDefaultChannel();
            if (defaultChannel != null) {
                defaultChannel.sendMessage("Give me a name! Type \"$botify rename Your Name\"").queue();
            } else {
                TextChannel systemChannel = guild.getSystemChannel();
                if (systemChannel != null) {
                    systemChannel.sendMessage("Give me a name! Type \"$botify rename Your Name\"").queue();
                }
            }
        } catch (InsufficientPermissionException e) {
            for (TextChannel textChannel : guild.getTextChannels()) {
                try {
                    textChannel.sendMessage("Give me a name! Type \"$botify rename Your Name\"").queue();
                    break;
                } catch (InsufficientPermissionException ignored){
                }
            }
        }
    }

}
