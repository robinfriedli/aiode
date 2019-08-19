package net.robinfriedli.botify.interceptors;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.parser.ArgumentBuildingMode;
import net.robinfriedli.botify.command.parser.CommandParseListener;
import net.robinfriedli.botify.command.parser.CommandParser;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.properties.AbstractGuildProperty;
import net.robinfriedli.botify.discord.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.discord.properties.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.Preset;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

public class GuildPropertyInterceptor extends ChainableInterceptor {

    private final CommandContext commandContext;
    private final GuildPropertyManager guildPropertyManager;
    private final Map<AbstractGuildProperty, Pair<Object, Object>> changedProperties;
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
                        changedProperties.put(property, Pair.of(previous, current));
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
                    Pair<Object, Object> previousWithNewValue = changedProperties.get(property);
                    successMessageBuilder.append(String.format(updateMessage, previousWithNewValue.getRight()))
                        .append(System.lineSeparator());

                    if ("argumentPrefix".equals(property.getProperty())) {
                        try {
                            // previous might be null
                            updatePresets((Character) previousWithNewValue.getLeft(), (char) previousWithNewValue.getRight());
                        } catch (Throwable e) {
                            messageService.sendException("Exception occurred while updating presets with new argument prefix. " +
                                    "Presets will have to be updated manually if necessary.",
                                commandContext.getChannel());
                            LoggerFactory.getLogger(getClass()).error("Exception while updating presets", e);
                        }
                    }
                }

                messageService.sendSuccess(successMessageBuilder.toString(), commandContext.getChannel());
            }
        }
        changedProperties.clear();
    }

    private void updatePresets(Character oldArgumentPrefix, char newArgumentPrefix) {
        Session session = commandContext.getSession();
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<Preset> presetQuery = cb.createQuery(Preset.class);
        Root<Preset> queryRoot = presetQuery.from(Preset.class);
        presetQuery.where(cb.equal(queryRoot.get("guildId"), commandContext.getGuild().getId()));
        List<Preset> presets = session.createQuery(presetQuery).getResultList();

        for (Preset preset : presets) {
            Botify botify = Botify.get();
            AbstractCommand command = preset.instantiateCommand(botify.getCommandManager(), commandContext, preset.getName());
            char argPrefix = oldArgumentPrefix != null ? oldArgumentPrefix : ArgumentPrefixProperty.DEFAULT;

            String oldPreset = preset.getPreset();
            StringBuilder newPresetBuilder = new StringBuilder(oldPreset);
            List<Integer> oldPrefixOccurrences = Lists.newArrayList();
            CommandParser commandParser = new CommandParser(command, argPrefix, new CommandParseListener() {
                @Override
                public void onModeSwitch(CommandParser.Mode previousMode, CommandParser.Mode newMode, int index, char character) {
                    // to be 100% certain that this only migrates the characters that are actually non-escaped argument prefixes
                    // only replace those characters that cause a mode switch to a new ArgumentBuildingMode, meaning
                    // they are in fact argument prefixes
                    if (newMode instanceof ArgumentBuildingMode) {
                        oldPrefixOccurrences.add(index);
                    }
                }
            });
            commandParser.parse(oldPreset);

            for (int oldPrefixOccurrence : oldPrefixOccurrences) {
                newPresetBuilder.setCharAt(oldPrefixOccurrence, newArgumentPrefix);
            }

            String newPreset = newPresetBuilder.toString();
            if (!oldPreset.equals(newPreset)) {
                preset.setPreset(newPreset);
            }
        }
    }

}
