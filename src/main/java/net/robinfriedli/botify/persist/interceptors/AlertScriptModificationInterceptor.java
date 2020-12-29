package net.robinfriedli.botify.persist.interceptors;

import java.util.List;

import org.slf4j.Logger;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.StoredScript;
import org.hibernate.Interceptor;

public class AlertScriptModificationInterceptor extends CollectingInterceptor {

    private final CommandContext commandContext;
    private final MessageService messageService;

    public AlertScriptModificationInterceptor(Interceptor next,
                                              Logger logger,
                                              CommandContext commandContext,
                                              MessageService messageService) {
        super(next, logger);
        this.commandContext = commandContext;
        this.messageService = messageService;
    }

    @Override
    public void afterCommit() {
        List<StoredScript> createdEntities = getCreatedEntities(StoredScript.class);
        List<StoredScript> deletedEntities = getDeletedEntities(StoredScript.class);
        List<StoredScript> updatedEntities = getUpdatedEntities(StoredScript.class);

        alertScriptModification("Created", createdEntities);
        alertScriptModification("Deleted", deletedEntities);

        List<StoredScript> activatedScripts = Lists.newArrayList();
        List<StoredScript> deactivatedScripts = Lists.newArrayList();
        for (StoredScript updatedEntity : updatedEntities) {
            if (isFieldTouched(updatedEntity, "active")) {
                Object prevVal = getOriginalValue(updatedEntity, "active");
                if (prevVal instanceof Boolean) {
                    boolean wasActive = (boolean) prevVal;

                    if (!wasActive && updatedEntity.isActive()) {
                        activatedScripts.add(updatedEntity);
                    } else if (wasActive && !updatedEntity.isActive()) {
                        deactivatedScripts.add(updatedEntity);
                    }
                }
            }
        }

        alertScriptModification("Activated", activatedScripts);
        alertScriptModification("Deactivated", deactivatedScripts);
    }

    private void alertScriptModification(String verb, List<StoredScript> affectedEntities) {
        if (!affectedEntities.isEmpty()) {
            if (affectedEntities.size() == 1) {
                StoredScript script = affectedEntities.get(0);
                messageService.sendSuccess(String.format("%s script '%s'", verb, script.getIdentifier()), commandContext.getChannel());
            } else {
                messageService.sendSuccess(String.format("%s %d scripts", verb, affectedEntities.size()), commandContext.getChannel());
            }
        }
    }

}
