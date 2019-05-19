package net.robinfriedli.botify.discord;

import java.awt.Color;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.util.ISnowflakeMap;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import net.robinfriedli.jxp.queries.Query;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class GuildManager {

    private final Context specificationContext;
    private final ISnowflakeMap<GuildContext> guildContexts = new ISnowflakeMap<>();
    private AudioManager audioManager;

    public GuildManager(Context specificationContext) {
        this.specificationContext = specificationContext;
    }

    public void addGuild(Guild guild) {
        initializeGuild(guild);
    }

    public String getNameForGuild(Guild guild) {
        return getContextForGuild(guild).getSpecification().getName();
    }

    @Nullable
    public String getPrefixForGuild(Guild guild) {
        GuildSpecification guildSpecification = getContextForGuild(guild).getSpecification();
        if (guildSpecification.hasAttribute("prefix")) {
            return guildSpecification.getAttribute("prefix").getValue();
        }

        return null;
    }

    public boolean setName(Guild guild, String name) {
        GuildSpecification guildSpecification = getContextForGuild(guild).getSpecification();
        specificationContext.invoke(() -> guildSpecification.setAttribute("botifyName", name));
        try {
            guild.getController().setNickname(guild.getSelfMember(), name).queue();
            return true;
        } catch (InsufficientPermissionException ignored) {
            return false;
        }
    }

    public void setPrefix(Guild guild, String prefix) {
        GuildSpecification guildSpecification = getContextForGuild(guild).getSpecification();
        specificationContext.invoke(() -> guildSpecification.setAttribute("prefix", prefix));
    }

    public boolean checkAccess(String commandIdentifier, Member member) {
        AccessConfiguration accessConfiguration = getAccessConfiguration(commandIdentifier, member.getGuild());
        return accessConfiguration == null || member.isOwner() || accessConfiguration.canAccess(member);
    }

    @Nullable
    public AccessConfiguration getAccessConfiguration(String commandIdentifier, Guild guild) {
        GuildSpecification guildSpecification = getContextForGuild(guild).getSpecification();
        return (AccessConfiguration) Query.evaluate(and(
            instanceOf(AccessConfiguration.class),
            attribute("commandIdentifier").is(commandIdentifier)
        )).execute(guildSpecification.getSubElements()).getOnlyResult();
    }

    public void registerAccessConfiguration(String commandIdentifier, List<Role> roles, Guild guild) {
        GuildSpecification guildSpecification = getContextForGuild(guild).getSpecification();
        AccessConfiguration existingAccessConfiguration = getAccessConfiguration(commandIdentifier, guild);

        if (existingAccessConfiguration != null) {
            throw new IllegalStateException("Access configuration for command " + commandIdentifier + " already exists.");
        }

        AccessConfiguration accessConfiguration = new AccessConfiguration(commandIdentifier, roles, specificationContext);
        specificationContext.invoke(() -> guildSpecification.addSubElement(accessConfiguration));
    }

    public GuildContext getContextForGuild(Guild guild) {
        GuildContext guildContext = guildContexts.get(guild);

        if (guildContext == null) {
            return initializeGuild(guild);
        }

        return guildContext;
    }

    public Collection<GuildContext> getGuildContexts() {
        return guildContexts.values();
    }

    public void setAudioManager(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    private GuildContext initializeGuild(Guild guild) {
        AudioPlayer player = audioManager.getPlayerManager().createPlayer();
        player.addListener(audioManager);

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

            GuildContext guildContext = new GuildContext(new AudioPlayback(player, guild), (GuildSpecification) existingSpecification);
            guildContexts.put(guild, guildContext);
            return guildContext;
        } else {
            GuildSpecification newSpecification = specificationContext.invoke(() -> {
                GuildSpecification guildSpecification = new GuildSpecification(guild, specificationContext);
                AccessConfiguration permissionConfiguration = new AccessConfiguration("permission", Lists.newArrayList(), specificationContext);
                guildSpecification.addSubElement(permissionConfiguration);
                guildSpecification.persist();
                return guildSpecification;
            });

            GuildContext guildContext = new GuildContext(new AudioPlayback(player, guild), newSpecification);
            guildContexts.put(guild, guildContext);

            alertNameChange(guild);
            return guildContext;
        }
    }

    private void alertNameChange(Guild guild) {
        MessageService messageService = new MessageService();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.decode("#1DB954"));
        embedBuilder.setDescription("Give me a name! Type \"$botify rename Your Name\"" + System.lineSeparator() +
            "Hint: The name can be used as command prefix. Alternatively you can define a command prefix using the prefix command.");
        messageService.send(embedBuilder.build(), guild);
    }

}
