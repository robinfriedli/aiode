package net.robinfriedli.botify.command.widget;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.PermissionTarget;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.entities.xml.WidgetContribution;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Manager class providing access to the static widget configuration
 */
@Component
public class WidgetManager {

    private final Map<Class<? extends AbstractWidget>, WidgetContribution> widgetContributionMap = new HashMap<>();
    private final Map<Class<? extends AbstractWidget>, Map<String, WidgetActionDefinition>> widgetReactionMap = new HashMap<>();
    private final Map<String, WidgetActionId> widgetActionSingletonMap = new HashMap<>();

    private final Context widgetConfigurationContext;

    public WidgetManager(
        CommandManager commandManager,
        JxpBackend jxpBackend,
        @Value("classpath:xml-contributions/widgets.xml") Resource widgetsResource
    ) {
        try {
            widgetConfigurationContext = jxpBackend.createContext(widgetsResource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Could not instantiate " + getClass().getSimpleName(), e);
        }

        for (WidgetContribution widgetContribution : widgetConfigurationContext.getInstancesOf(WidgetContribution.class)) {
            for (WidgetContribution.WidgetActionContribution widgetActionContribution : widgetContribution.getSubElementsWithType(WidgetContribution.WidgetActionContribution.class)) {
                String identifier = widgetActionContribution.getIdentifier();
                WidgetActionId widgetActionId = widgetActionSingletonMap.computeIfAbsent(identifier, id -> {
                    CommandContribution referencedCommand = commandManager.getCommandContribution(id);
                    return new WidgetActionId(id, referencedCommand);
                });

                Map<String, WidgetActionDefinition> reactionMap = widgetReactionMap.computeIfAbsent(widgetContribution.getImplementationClass(), widgetType -> new LinkedHashMap<>());

                WidgetActionDefinition widgetActionDefinition = new WidgetActionDefinition(widgetActionId, widgetActionContribution);
                reactionMap.put(widgetActionContribution.getEmojiUnicode(), widgetActionDefinition);
            }

            widgetContributionMap.put(widgetContribution.getImplementationClass(), widgetContribution);
        }
    }

    public Optional<WidgetActionId> findWidgetActionSingleton(String identifier) {
        return Optional.ofNullable(widgetActionSingletonMap.get(identifier));
    }

    public Collection<WidgetActionId> getWidgetActionSingletons() {
        return widgetActionSingletonMap.values();
    }

    public WidgetContribution getContributionForWidget(Class<? extends AbstractWidget> type) {
        WidgetContribution widgetContribution = widgetContributionMap.get(type);

        if (widgetContribution == null) {
            throw new IllegalStateException(String.format("Missing widget contribution for class %s in widgets.xml", type.getName()));
        }

        return widgetContribution;
    }

    public Optional<WidgetActionDefinition> getWidgetActionDefinitionForReaction(Class<? extends AbstractWidget> widgetType, MessageReaction reaction) {
        Map<String, WidgetActionDefinition> reactionMap = requireReactionMapForWidgetType(widgetType);
        return Optional.ofNullable(reactionMap.get(reaction.getReactionEmote().getName()));
    }

    /**
     * Return a list containing all mapped actions for the provided widget type in the order in which they
     * were inserted to map, i.e. the order in which they appear in the widgets.xml file.
     *
     * @param widgetType the class of the widget
     * @return a collection containing a view of all mapped actions, ordered
     */
    public List<WidgetActionDefinition> getActionsForWidget(Class<? extends AbstractWidget> widgetType) {
        Map<String, WidgetActionDefinition> reactionMap = requireReactionMapForWidgetType(widgetType);
        return Lists.newArrayList(reactionMap.values());
    }

    public Context getWidgetConfigurationContext() {
        return widgetConfigurationContext;
    }

    private Map<String, WidgetActionDefinition> requireReactionMapForWidgetType(Class<? extends AbstractWidget> widgetType) {
        Map<String, WidgetActionDefinition> reactionMap = widgetReactionMap.get(widgetType);

        if (reactionMap == null) {
            throw new IllegalStateException("No configuration mapped for widget class " + widgetType.getName());
        }

        return reactionMap;
    }

    /**
     * A specific action defined for a widget, containing the corresponding {@link WidgetContribution.WidgetActionContribution}
     * configuration and unique {@link WidgetActionId}.
     */
    public static class WidgetActionDefinition {

        private final WidgetActionId id;
        private final WidgetContribution.WidgetActionContribution implementation;

        public WidgetActionDefinition(WidgetActionId id, WidgetContribution.WidgetActionContribution implementation) {
            this.id = id;
            this.implementation = implementation;
        }

        public WidgetActionId getId() {
            return id;
        }

        public WidgetContribution.WidgetActionContribution getImplementation() {
            return implementation;
        }
    }

    /**
     * Unique widget action id that be used by several {@link WidgetActionDefinition} for different widgets sharing the
     * same actions. Optionally references the matching {@link CommandContribution} if a command with the same id exists,
     * in which case the widget actions shares the command's permission target.
     */
    public static class WidgetActionId implements PermissionTarget {

        private final String identifier;
        @Nullable
        private final CommandContribution referencedCommand;

        public WidgetActionId(String identifier, @Nullable CommandContribution referencedCommand) {
            this.identifier = identifier;
            this.referencedCommand = referencedCommand;
        }

        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String getPermissionTargetIdentifier() {
            if (referencedCommand != null) {
                return referencedCommand.getPermissionTargetIdentifier();
            }
            return getIdentifier();
        }

        @Override
        public TargetType getPermissionTargetType() {
            if (referencedCommand != null) {
                return referencedCommand.getPermissionTargetType();
            }
            return TargetType.WIDGET;
        }

        @Nullable
        @Override
        public PermissionTarget getChildTarget(String identifier) {
            if (referencedCommand != null) {
                return referencedCommand.getChildTarget(identifier);
            }
            return null;
        }

        @Nullable
        @Override
        public Set<? extends PermissionTarget> getChildren() {
            if (referencedCommand != null) {
                return referencedCommand.getChildren();
            }
            return null;
        }

        @Nullable
        @Override
        public PermissionTarget getParentTarget() {
            if (referencedCommand != null) {
                return referencedCommand.getParentTarget();
            }
            return null;
        }
    }

}
