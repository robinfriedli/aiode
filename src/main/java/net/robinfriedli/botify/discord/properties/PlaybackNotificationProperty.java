package net.robinfriedli.botify.discord.properties;

import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidPropertyValueException;

public class PlaybackNotificationProperty extends AbstractGuildProperty {

    public PlaybackNotificationProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void validate(Object state) {
    }

    @Override
    public Object process(String input) {
        if (!(input.equalsIgnoreCase("false") || input.equalsIgnoreCase("true"))) {
            throw new InvalidPropertyValueException("Value must be a boolean");
        }
        return Boolean.parseBoolean(input);
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        guildSpecification.setSendPlaybackNotification((boolean) process(value));
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.isSendPlaybackNotification();
    }
}
