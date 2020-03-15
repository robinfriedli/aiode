package net.robinfriedli.botify.discord.property.properties;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.AmbiguousCommandException;
import net.robinfriedli.botify.exceptions.InvalidPropertyValueException;

/**
 * Property that defines the default text channel to use for each guild when no explicit channel is specified, for example
 * when sending update announcements.
 */
public class DefaultTextChannelProperty extends AbstractGuildProperty {

    public DefaultTextChannelProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void doValidate(Object state) {
    }

    @Override
    public Object process(String input) {
        return input;
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        ShardManager shardManager = Botify.get().getShardManager();
        Guild guild = guildSpecification.getGuild(shardManager);
        TextChannel textChannelById = null;
        try {
            textChannelById = guild.getTextChannelById(value);
        } catch (NumberFormatException ignored) {
        }
        if (textChannelById != null) {
            guildSpecification.setDefaultTextChannelId(value);
        } else {
            List<TextChannel> textChannelsByName = guild.getTextChannelsByName(value, true);
            if (textChannelsByName.size() == 1) {
                guildSpecification.setDefaultTextChannelId(textChannelsByName.get(0).getId());
            } else if (textChannelsByName.size() > 1) {
                throw new AmbiguousCommandException(textChannelsByName, c -> {
                    TextChannel channel = (TextChannel) c;
                    return channel.getName() + " (" + channel.getId() + ")";
                });
            } else {
                throw new InvalidPropertyValueException(String.format("No text channel found for '%s'. Either copy the id of the channel or its name.", value));
            }
        }
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getDefaultTextChannelId();
    }

    @Override
    public Object get(GuildSpecification specification) {
        return extractPersistedValue(specification);
    }

    @Override
    public String display(GuildSpecification guildSpecification) {
        Object persistedValue = extractPersistedValue(guildSpecification);
        if (persistedValue != null) {
            Guild guild = guildSpecification.getGuild(Botify.get().getShardManager());
            TextChannel textChannelById = guild.getTextChannelById((String) persistedValue);
            if (textChannelById != null) {
                return textChannelById.getName();
            } else {
                return "DELETED CHANNEL";
            }
        } else {
            return "Not Set";
        }
    }
}
