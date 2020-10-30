package net.robinfriedli.botify.persist.interceptors;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.botify.function.HibernateInvoker;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

public class GuildPropertyInterceptor extends ChainableInterceptor {

    private final CommandContext commandContext;
    private final GuildPropertyManager guildPropertyManager;
    private final Map<AbstractGuildProperty, Pair<Object, Object>> changedProperties;
    private final MessageService messageService;
    private final QueryBuilderFactory queryBuilderFactory;
    private final SessionFactory sessionFactory;

    public GuildPropertyInterceptor(Interceptor next,
                                    Logger logger,
                                    CommandContext commandContext,
                                    GuildPropertyManager guildPropertyManager,
                                    MessageService messageService,
                                    QueryBuilderFactory queryBuilderFactory,
                                    SessionFactory sessionFactory) {
        super(next, logger);
        this.commandContext = commandContext;
        this.guildPropertyManager = guildPropertyManager;
        this.messageService = messageService;
        this.queryBuilderFactory = queryBuilderFactory;
        this.sessionFactory = sessionFactory;
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
    public void beforeTransactionCompletionChained(Transaction tx) {
        if (!tx.getRollbackOnly()) {
            for (AbstractGuildProperty property : changedProperties.keySet()) {
                if ("argumentPrefix".equals(property.getProperty())) {
                    // a new transaction has to be started explicitly as this one cannot be used anymore, this also requires
                    // a new session where this interceptor is not registered, otherwise it'd be stuck in an infinite
                    // loop of triggering itself
                    try (Session session = sessionFactory.openSession()) {
                        HibernateInvoker.create(session).invoke(() -> {
                            Pair<Object, Object> previousWithNewValue = changedProperties.get(property);
                            // previous might be null
                            updatePresets(property, (Character) previousWithNewValue.getLeft(), (char) previousWithNewValue.getRight(), session);
                        });
                    } catch (Exception e) {
                        messageService.sendException("Exception occurred while updating presets with new argument prefix. " +
                                "Presets will have to be updated manually if necessary.",
                            commandContext.getChannel());
                        LoggerFactory.getLogger(getClass()).error("Exception while updating presets", e);
                    }
                }
            }
        }
    }

    @Override
    public void afterTransactionCompletionChained(Transaction tx) {
        if (!tx.getRollbackOnly()) {
            if (!changedProperties.isEmpty()) {
                StringBuilder successMessageBuilder = new StringBuilder();
                for (AbstractGuildProperty property : changedProperties.keySet()) {
                    Pair<Object, Object> previousWithNewValue = changedProperties.get(property);
                    String updateMessage = property.getContribution().getUpdateMessage(previousWithNewValue.getRight());
                    successMessageBuilder.append(updateMessage).append(System.lineSeparator());
                }

                messageService.sendSuccess(successMessageBuilder.toString(), commandContext.getChannel());
            }
        }
        changedProperties.clear();
    }

    private void updatePresets(AbstractGuildProperty argumentPrefixProperty, Character oldArgumentPrefix, char newArgumentPrefix, Session session) {
        List<Preset> presets = queryBuilderFactory.find(Preset.class).build(session).getResultList();

        String defaultValue = argumentPrefixProperty.getDefaultValue();
        char[] chars = defaultValue.toCharArray();
        char defaultArgPrefix;

        if (chars.length == 1) {
            defaultArgPrefix = chars[0];
        } else {
            defaultArgPrefix = ArgumentPrefixProperty.DEFAULT_FALLBACK;
        }

        char argPrefix = oldArgumentPrefix != null ? oldArgumentPrefix : defaultArgPrefix;
        ArgumentPrefixProperty.Config argumentPrefixConfig = new ArgumentPrefixProperty.Config(argPrefix, defaultArgPrefix);

        for (Preset preset : presets) {
            Botify botify = Botify.get();
            AbstractCommand command = preset.instantiateCommand(botify.getCommandManager(), commandContext, preset.getName());

            String oldPreset = preset.getPreset();
            StringBuilder newPresetBuilder = new StringBuilder(oldPreset);
            List<Integer> oldPrefixOccurrences = Lists.newArrayList();
            CommandParser commandParser = new CommandParser(command, argumentPrefixConfig, new CommandParseListener() {
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
