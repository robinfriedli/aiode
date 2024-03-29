package net.robinfriedli.aiode.discord.property.properties;

import net.robinfriedli.aiode.discord.listeners.VoiceChannelListener;
import net.robinfriedli.aiode.discord.property.AbstractBoolProperty;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;

/**
 * Property that enables / disables auto pause, meaning the bot will automatically pause the playback and leave the channel
 * if all members in a voice channel leave. This is handled by the {@link VoiceChannelListener}
 */
public class AutoPauseProperty extends AbstractBoolProperty {

    public AutoPauseProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    @Override
    protected void setBoolValue(boolean bool, GuildSpecification guildSpecification) {
        guildSpecification.setEnableAutoPause(bool);
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.isEnableAutoPause();
    }
}
