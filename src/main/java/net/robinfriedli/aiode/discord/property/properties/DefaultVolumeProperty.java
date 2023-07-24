package net.robinfriedli.aiode.discord.property.properties;

import java.util.Objects;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.discord.property.AbstractIntegerProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;
import net.robinfriedli.aiode.exceptions.InvalidPropertyValueException;

/**
 * Property that toggles "Now playing..." messages
 */
public class DefaultVolumeProperty extends AbstractIntegerProperty {

    public DefaultVolumeProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    public void validate(Object state) {
        int volume = (Integer) state;
        if (volume < 1 || volume > 200) {
            throw new InvalidPropertyValueException("Volume must be between 1 and 200");
        }
    }

    @Override
    protected void setIntegerValue(Integer volume, GuildSpecification guildSpecification) {
        ExecutionContext executionContext = ExecutionContext.Current.get();
        if (executionContext != null) {
            AudioPlayback playback = Aiode.get().getAudioManager().getPlaybackForGuild(executionContext.getGuild());
            playback.setDefaultVolume(Objects.requireNonNullElse(volume, 100));
        }
        guildSpecification.setDefaultVolume(volume);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getDefaultVolume();
    }
}
