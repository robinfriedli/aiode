package net.robinfriedli.botify.command;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.Sets;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.argument.CommandArgument;
import net.robinfriedli.botify.command.widget.WidgetManager;
import net.robinfriedli.botify.discord.property.properties.PrefixProperty;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.CustomPermissionTarget;
import net.robinfriedli.botify.entities.LookupEntity;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import org.hibernate.Session;

/**
 * Interface describing any object for which permission may be set up and checked. Includes command, arguments, widgets
 * and custom permission targets.
 */
public interface PermissionTarget {

    /**
     * The full target identifier path starting with the root target identifier, using "$" as separator, e.g. command$arg
     *
     * @return the full identifier of this target
     */
    default String getFullPermissionTargetIdentifier() {
        PermissionTarget parent = getParentTarget();

        if (parent != null) {
            return parent.getFullPermissionTargetIdentifier() + "$" + getPermissionTargetIdentifier();
        } else {
            return getPermissionTargetIdentifier();
        }
    }

    /**
     * @return the identifier of this specific target, without the full path
     */
    String getPermissionTargetIdentifier();

    TargetType getPermissionTargetType();

    @Nullable
    PermissionTarget getChildTarget(String identifier);

    @Nullable
    Set<? extends PermissionTarget> getChildren();

    @Nullable
    PermissionTarget getParentTarget();

    default PermissionTypeCategory getPermissionTypeCategory() {
        return getPermissionTargetType();
    }

    enum TargetType implements PermissionTypeCategory {

        COMMAND("command", false) {
            @Override
            public Optional<? extends PermissionTarget> findPermissionTarget(String identifier) {
                CommandManager commandManager = Botify.get().getCommandManager();
                return Optional.ofNullable(commandManager.getCommandContribution(identifier));
            }

            @Override
            public Set<? extends PermissionTarget> getAllPermissionTargetsInCategory() {
                CommandManager commandManager = Botify.get().getCommandManager();
                return commandManager.getCommandContributions()
                    .stream()
                    .filter(commandContribution -> commandContribution.getCategory() != AbstractCommand.Category.ADMIN)
                    .collect(Collectors.toSet());
            }

            @Override
            public PermissionTypeCategory[] getSubCategories() {
                return AbstractCommand.Category.values();
            }

            @Override
            public MessageEmbed.Field createEmbedField() {
                return new MessageEmbed.Field(getName(), "Permission targets for standard commands.", false);
            }
        },
        ARGUMENT("argument", true) {
            @Override
            public Optional<? extends PermissionTarget> findPermissionTarget(String identifier) {
                CommandManager commandManager = Botify.get().getCommandManager();
                String[] split = identifier.split("\\$");
                if (split.length == 2) {
                    CommandContribution commandContribution = commandManager.getCommandContribution(split[0]);
                    if (commandContribution != null) {
                        return Optional.ofNullable(commandContribution.getArgument(split[1]));
                    }
                }

                return Optional.empty();
            }

            @Override
            public Set<? extends PermissionTarget> getAllPermissionTargetsInCategory() {
                CommandManager commandManager = Botify.get().getCommandManager();
                Set<CommandArgument> commandArguments = Sets.newHashSet();

                for (CommandContribution commandContribution : commandManager.getCommandContributions()) {
                    commandArguments.addAll(commandContribution.getArguments().values());
                }

                return commandArguments;
            }

            @Override
            public MessageEmbed.Field createEmbedField() {
                return new MessageEmbed.Field(
                    "arguments",
                    String.format(
                        "For available arguments check the help page of the corresponding command (e.g. `%shelp play`). These permissions cannot be managed by the $category argument.",
                        PrefixProperty.getEffectiveCommandStartForCurrentContext()
                    ),
                    false
                );
            }
        },
        CUSTOM("permission", false) {
            @Override
            public Optional<? extends PermissionTarget> findPermissionTarget(String identifier) {
                Botify botify = Botify.get();
                QueryBuilderFactory queryBuilderFactory = botify.getQueryBuilderFactory();
                HibernateComponent hibernateComponent = botify.getHibernateComponent();

                return hibernateComponent.invokeWithSession(session ->
                    queryBuilderFactory.find(CustomPermissionTarget.class)
                        .where((cb, root) -> cb.equal(cb.lower(root.get("identifier")), identifier.toLowerCase()))
                        .build(session)
                        .uniqueResultOptional()
                );
            }

            @Override
            public Set<? extends PermissionTarget> getAllPermissionTargetsInCategory() {
                Botify botify = Botify.get();
                QueryBuilderFactory queryBuilderFactory = botify.getQueryBuilderFactory();
                HibernateComponent hibernateComponent = botify.getHibernateComponent();
                return hibernateComponent.invokeWithSession(session ->
                    queryBuilderFactory
                        .find(CustomPermissionTarget.class)
                        .build(session)
                        .getResultStream()
                        .collect(Collectors.toSet())
                );
            }

            @Override
            public MessageEmbed.Field createEmbedField() {
                return createStandardEmbedField("custom", "No custom permission targets saved");
            }
        },
        WIDGET("widget action", false) {
            @Override
            public Optional<? extends PermissionTarget> findPermissionTarget(String identifier) {
                WidgetManager widgetManager = Botify.get().getWidgetManager();
                Optional<WidgetManager.WidgetActionId> widgetActionSingleton = widgetManager.findWidgetActionSingleton(identifier);

                if (widgetActionSingleton.isPresent()) {
                    if (widgetActionSingleton.get().getPermissionTargetType() == WIDGET) {
                        return widgetActionSingleton;
                    } else {
                        return Optional.empty();
                    }
                }

                return widgetActionSingleton;
            }

            @Override
            public Set<? extends PermissionTarget> getAllPermissionTargetsInCategory() {
                WidgetManager widgetManager = Botify.get().getWidgetManager();
                return widgetManager
                    .getWidgetActionSingletons()
                    .stream()
                    .filter(action -> action.getPermissionTargetType() == WIDGET)
                    .collect(Collectors.toSet());
            }

            @Override
            public MessageEmbed.Field createEmbedField() {
                return createStandardEmbedField(getName(), "No applicable widget actions found");
            }
        };

        private final String name;
        private final boolean childTargetOnly;

        TargetType(String name, boolean childTargetOnly) {
            this.name = name;
            this.childTargetOnly = childTargetOnly;
        }

        public String getName() {
            return name;
        }

        public String getIdentifier() {
            return name();
        }


        @Override
        public String getCategoryName() {
            return getIdentifier();
        }

        @Override
        public String getCategoryIdentifier() {
            return getIdentifier();
        }

        @Nullable
        @Override
        public PermissionTypeCategory getParentCategory() {
            return null;
        }

        @Nullable
        public PermissionTypeCategory[] getSubCategories() {
            return null;
        }

        @Override
        public int getOrdinal() {
            return this.ordinal();
        }


        public AccessConfiguration.PermissionType getEntity(Session session) {
            return LookupEntity.require(session, AccessConfiguration.PermissionType.class, this.name());
        }

        public boolean isChildTargetOnly() {
            return childTargetOnly;
        }

        protected MessageEmbed.Field createStandardEmbedField(String name, String emptyMessage) {
            StringBuilder customTargetStringBuilder = new StringBuilder();
            Set<? extends PermissionTarget> customTargets = getAllPermissionTargetsInCategory();
            if (!customTargets.isEmpty()) {
                Iterator<? extends PermissionTarget> iterator = customTargets.iterator();
                while (iterator.hasNext()) {
                    PermissionTarget customTarget = iterator.next();
                    customTargetStringBuilder.append(customTarget.getFullPermissionTargetIdentifier());

                    if (iterator.hasNext()) {
                        customTargetStringBuilder.append(System.lineSeparator());
                    }
                }
            } else {
                customTargetStringBuilder.append(emptyMessage);
            }

            return new MessageEmbed.Field(name, customTargetStringBuilder.toString(), false);
        }

    }

    /**
     * Enables organising permission targets in categories, the root categories are described by {@link TargetType}, whereas
     * the {@link TargetType#COMMAND} has the command categories as sub categories.
     */
    interface PermissionTypeCategory {

        String getCategoryName();

        Optional<? extends PermissionTarget> findPermissionTarget(String identifier);

        Set<? extends PermissionTarget> getAllPermissionTargetsInCategory();

        String getCategoryIdentifier();

        @Nullable
        PermissionTypeCategory getParentCategory();

        @Nullable
        MessageEmbed.Field createEmbedField();

        default String getFullCategoryName() {
            PermissionTypeCategory parentCategory = getParentCategory();
            if (parentCategory != null) {
                return parentCategory.getFullCategoryName() + "$" + getCategoryName();
            } else {
                return getCategoryName();
            }
        }

        default String getFullCategoryIdentifier() {
            PermissionTypeCategory parentCategory = getParentCategory();
            if (parentCategory != null) {
                return parentCategory.getFullCategoryIdentifier() + "$" + getCategoryIdentifier();
            } else {
                return getCategoryIdentifier();
            }
        }

        @Nullable
        PermissionTypeCategory[] getSubCategories();

        int getOrdinal();

        default int getSorting() {
            int multiplier = 1000000;
            PermissionTypeCategory parentCategory = getParentCategory();
            PermissionTypeCategory currentParent = parentCategory;
            while (currentParent != null) {
                multiplier /= 10;
                currentParent = currentParent.getParentCategory();
            }

            int multipliedOrdinal = multiplier * (getOrdinal() + 1);
            int offset;
            if (parentCategory != null) {
                offset = parentCategory.getSorting();
            } else {
                offset = 0;
            }

            return offset + multipliedOrdinal;
        }

    }

}
