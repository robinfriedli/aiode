package net.robinfriedli.aiode.persist.interceptors;

import java.util.List;

import org.slf4j.Logger;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.entities.Preset;
import net.robinfriedli.stringlist.StringList;
import org.hibernate.Interceptor;

public class AlertPresetCreationInterceptor extends CollectingInterceptor {

    private final MessageChannel channel;
    private final MessageService messageService;

    public AlertPresetCreationInterceptor(Interceptor next, Logger logger, ExecutionContext executionContext, MessageService messageService) {
        super(next, logger);
        channel = executionContext.getChannel();
        this.messageService = messageService;
    }

    @Override
    public void afterCommit() {
        List<Preset> createdPresets = getCreatedEntities(Preset.class);
        List<Preset> deletedPresets = getDeletedEntities(Preset.class);


        if (!createdPresets.isEmpty()) {
            if (createdPresets.size() == 1) {
                Preset createdPreset = createdPresets.get(0);
                messageService.sendSuccess("Saved preset " + createdPreset.getName(), channel);
            } else {
                messageService.sendSuccess("Saved presets "
                    + StringList.create(createdPresets, Preset::getName).toSeparatedString(", "), channel);
            }
        }

        if (!deletedPresets.isEmpty()) {
            if (deletedPresets.size() == 1) {
                Preset deletedPreset = deletedPresets.get(0);
                messageService.sendSuccess("Deleted preset " + deletedPreset.getName(), channel);
            } else {
                messageService.sendSuccess("Deleted presets "
                    + StringList.create(deletedPresets, Preset::getName).toSeparatedString(", "), channel);
            }
        }
    }
}
