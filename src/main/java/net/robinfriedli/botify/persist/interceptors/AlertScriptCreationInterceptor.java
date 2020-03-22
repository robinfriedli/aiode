package net.robinfriedli.botify.persist.interceptors;

import java.util.List;

import org.slf4j.Logger;

import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.StoredScript;
import org.hibernate.Interceptor;

public class AlertScriptCreationInterceptor extends CollectingInterceptor {

    private final CommandContext commandContext;
    private final MessageService messageService;

    public AlertScriptCreationInterceptor(Interceptor next,
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

        if (!createdEntities.isEmpty()) {
            if (createdEntities.size() == 1) {
                StoredScript script = createdEntities.get(0);
                messageService.sendSuccess(String.format("Created script '%s'", script.getIdentifier()), commandContext.getChannel());
            } else {
                messageService.sendSuccess(String.format("Created %d scripts", createdEntities.size()), commandContext.getChannel());
            }
        }

        if (!deletedEntities.isEmpty()) {
            if (deletedEntities.size() == 1) {
                StoredScript script = deletedEntities.get(0);
                messageService.sendSuccess(String.format("Deleted script '%s'", script.getIdentifier()), commandContext.getChannel());
            } else {
                messageService.sendSuccess(String.format("Deleted %d scripts", deletedEntities.size()), commandContext.getChannel());
            }
        }
    }

}
