package net.robinfriedli.botify.interceptors;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.properties.AbstractGuildProperty;
import net.robinfriedli.botify.discord.properties.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

public class GuildPropertyInterceptor extends ChainableInterceptor {

    private final CommandContext commandContext;
    private final GuildPropertyManager guildPropertyManager;
    private final Map<AbstractGuildProperty, Object> changedProperties;
    private final MessageService messageService;

    public GuildPropertyInterceptor(Interceptor next, Logger logger, CommandContext commandContext, GuildPropertyManager guildPropertyManager, MessageService messageService) {
        super(next, logger);
        this.commandContext = commandContext;
        this.guildPropertyManager = guildPropertyManager;
        this.messageService = messageService;
        changedProperties = new HashMap<>();
    }

    // use onFlushDirty instead of onFlushDirtyChained as exceptions should get thrown
    @Override
    public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
        if (entity instanceof GuildSpecification) {
            for (int i = 0; i < currentState.length; i++) {
                String propertyName = propertyNames[i];
                Object current = currentState[i];
                Object previous = previousState[i];
                if (current != null && !current.equals(previous)) {
                    AbstractGuildProperty property = guildPropertyManager.getProperty(propertyName);
                    if (property != null) {
                        changedProperties.put(property, current);
                        property.validate(current);
                    }
                }
            }
        }
        return next().onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
    }

    @Override
    public void afterTransactionCompletionChained(Transaction tx) {
        if (!tx.getRollbackOnly()) {
            if (!changedProperties.isEmpty()) {
                StringBuilder successMessageBuilder = new StringBuilder();
                for (AbstractGuildProperty property : changedProperties.keySet()) {
                    String updateMessage = property.getContribution().getUpdateMessage();
                    successMessageBuilder.append(String.format(updateMessage, changedProperties.get(property)))
                        .append(System.lineSeparator());
                }

                messageService.sendSuccess(successMessageBuilder.toString(), commandContext.getChannel());
            }
        }
        changedProperties.clear();
    }
}
