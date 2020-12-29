package net.robinfriedli.botify.command;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.persist.qb.interceptor.interceptors.AccessConfigurationPartitionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manager that evaluates permission for a specific action represented by a command identifier or other permission target
 * for the given member or checks whether a user has admin privileges as configured in the settings-private.properties file.
 */
@Component
public class SecurityManager {

    private final HibernateComponent hibernateComponent;
    private final QueryBuilderFactory queryBuilderFactory;

    @Value("#{'${botify.security.admin_users}'.split('\\s+,\\s+')}")
    private List<String> adminUserIds;

    public SecurityManager(HibernateComponent hibernateComponent, QueryBuilderFactory queryBuilderFactory) {
        this.hibernateComponent = hibernateComponent;
        this.queryBuilderFactory = queryBuilderFactory;
    }

    /**
     * @param permissionIdentifier identifier of the permission target, usually a command (or argument or CustomPermissionTarget)
     * @param member               the member to check
     * @return true if the member has access to the provided permission target, either because the member has elevated
     * permissions (see {@link #hasElevatedPermissions(Member)}), no access configuration exists for the permission target
     * or the member has a role that has been granted access to the found {@link AccessConfiguration}.
     */
    public boolean askPermission(String permissionIdentifier, Member member) {
        return askPermission(requirePermissionTarget(permissionIdentifier), member);
    }

    /**
     * Check whether the provided member has access to the provided {@link PermissionTarget}. This is the case if the member
     * has elevated permissions (see {@link #hasElevatedPermissions(Member)}) or if no access configuration exists for the
     * provided target or the access configuration grants access to any role of the member.
     *
     * @param permissionTarget the permission target, e.g. command, argument, widget or custom permission target
     * @param member           the member to check
     * @return true if access has been granted
     */
    public boolean askPermission(PermissionTarget permissionTarget, Member member) {
        if (hasElevatedPermissions(member)) {
            return true;
        }

        Optional<AccessConfiguration> accessConfiguration = getAccessConfiguration(permissionTarget, member.getGuild());
        return accessConfiguration.isEmpty() || accessConfiguration.get().canAccess(member);
    }

    public Optional<AccessConfiguration> getAccessConfiguration(PermissionTarget permissionTarget, Guild guild) {
        return getAccessConfiguration(permissionTarget, guild.getId());
    }

    public Optional<AccessConfiguration> getAccessConfiguration(PermissionTarget permissionTarget, String guildId) {
        return hibernateComponent.invokeWithSession(session -> queryBuilderFactory.find(AccessConfiguration.class)
            .where((cb, root, subQueryFactory) -> cb.equal(root.get("permissionIdentifier"), permissionTarget.getFullPermissionTargetIdentifier()))
            .addInterceptors(new AccessConfigurationPartitionInterceptor(session, guildId))
            .build(session)
            .setCacheable(true)
            .uniqueResultOptional());
    }

    public boolean hasAccessConfiguration(PermissionTarget permissionTarget, Guild guild) {
        return hasAccessConfiguration(permissionTarget, guild.getId());
    }

    public boolean hasAccessConfiguration(PermissionTarget permissionTarget, String guildId) {
        return hibernateComponent.invokeWithSession(session -> {
            Long count = queryBuilderFactory.select(AccessConfiguration.class, (from, cb) -> cb.count(from.get("pk")), Long.class)
                .where((cb, root, subQueryFactory) -> cb.equal(root.get("permissionIdentifier"), permissionTarget.getFullPermissionTargetIdentifier()))
                .addInterceptors(new AccessConfigurationPartitionInterceptor(session, guildId))
                .build(session)
                .setCacheable(true)
                .uniqueResult();

            return count > 0;
        });
    }

    /**
     * Similar to {@link #askPermission(String, Member)} but instead of returning a boolean an exception is thrown if the
     * member does not have access to the provided permission target.
     *
     * @param permissionIdentifier identifier of the permission target, usually a command (or argument or CustomPermissionTarget)
     * @param member               the member to check
     * @throws ForbiddenCommandException if the member does not have access
     */
    public void ensurePermission(String permissionIdentifier, Member member) throws ForbiddenCommandException {
        ensurePermission(requirePermissionTarget(permissionIdentifier), member);
    }

    public void ensurePermission(PermissionTarget permissionTarget, Member member) throws ForbiddenCommandException {
        if (hasElevatedPermissions(member)) {
            return;
        }

        Optional<AccessConfiguration> accessConfiguration = getAccessConfiguration(permissionTarget, member.getGuild());
        if (!(accessConfiguration.isEmpty() || accessConfiguration.get().canAccess(member))) {
            Guild guild = member.getGuild();
            throw new ForbiddenCommandException(member.getUser(), permissionTarget, accessConfiguration.get().getRoles(guild));
        }
    }

    /**
     * @param member the member to check
     * @return true if the member is a bot admin, guild owner or has a role with {@link Permission#ADMINISTRATOR} rights
     */
    public boolean hasElevatedPermissions(Member member) {
        return isAdmin(member.getUser())
            || member.isOwner()
            || member.getRoles().stream().anyMatch(role -> role.hasPermission(Permission.ADMINISTRATOR));
    }

    /**
     * @param user the user to check
     * @return true if the user has been granted bot admin privileges by the botify.security.admin_users property.
     */
    public boolean isAdmin(User user) {
        return adminUserIds.contains(user.getId());
    }

    /**
     * Find a permission target (command, argument or custom permission target) via its identifier. The character '$'
     * denotes child permission targets, e.g. for argument 'list' of command 'play' the identifier looks like this:
     * 'play$list' (case insensitive), which is why this character cannot be used in the name of custom permission targets.
     *
     * @param identifier the identifier
     * @return the found permission target as an Optional
     */
    public Optional<? extends PermissionTarget> getPermissionTarget(String identifier) {
        if (identifier.contains("$")) {
            String[] path = identifier.split("\\$");
            Optional<? extends PermissionTarget> target = findRootTarget(path[0].trim());
            if (target.isPresent()) {
                PermissionTarget current = target.get();

                for (int i = 1; i < path.length; i++) {
                    current = current.getChildTarget(path[i].trim());
                    if (current == null) {
                        break;
                    }
                }

                return Optional.ofNullable(current);
            } else {
                return Optional.empty();
            }
        } else {
            return findRootTarget(identifier.trim());
        }
    }

    /**
     * Find all permission targets in a given category. The category identifier should describe the full path starting from
     * the root {@link PermissionTarget.TargetType} for subcategories, e.g. the identifier for the command category
     * PLAYBACK looks like this: 'command$playback' (case insensitive). Subcategories without the full path are only found
     * if the deep parameter is set.
     *
     * @param categoryIdentifier the full path of the category
     * @param deep               whether or not to recursively find subcategories if not explicitly provided in the path
     * @return an optional set containing all permission targets in the category
     */
    public Optional<Set<? extends PermissionTarget>> getPermissionTargetsByCategory(String categoryIdentifier, boolean deep) {
        if (categoryIdentifier.contains("$")) {
            String[] path = categoryIdentifier.split("\\$");
            Optional<PermissionTarget.PermissionTypeCategory> rootCategory = getRootCategory(path[0].trim());
            if (rootCategory.isPresent()) {
                PermissionTarget.PermissionTypeCategory current = rootCategory.get();
                for (int i = 1; i < path.length; i++) {
                    current = getSubCategory(path[i].trim(), current);
                    if (current == null) {
                        break;
                    }
                }

                return Optional
                    .ofNullable(current)
                    .map(PermissionTarget.PermissionTypeCategory::getAllPermissionTargetsInCategory);
            }
        } else {
            Optional<PermissionTarget.PermissionTypeCategory> rootCategory = getRootCategory(categoryIdentifier.trim());
            if (rootCategory.isPresent()) {
                return Optional.ofNullable(rootCategory.get().getAllPermissionTargetsInCategory());
            } else if (deep) {
                for (PermissionTarget.TargetType value : PermissionTarget.TargetType.values()) {
                    PermissionTarget.PermissionTypeCategory subcategory = findSubcategory(categoryIdentifier, value);

                    if (subcategory != null) {
                        return Optional.ofNullable(subcategory.getAllPermissionTargetsInCategory());
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Recursively find a subcategory in an arbitrarily deep structure of subcategories. In case several categories with
     * the same identifier exists across different levels this method is consistent in that it always returns the first
     * subcategory on the highest level.
     */
    @Nullable
    private PermissionTarget.PermissionTypeCategory findSubcategory(String identifier, PermissionTarget.PermissionTypeCategory currentCategory) {
        PermissionTarget.PermissionTypeCategory[] subCategories = currentCategory.getSubCategories();
        if (!(subCategories == null || subCategories.length == 0)) {
            PermissionTarget.PermissionTypeCategory foundInLowerCategory = null;
            for (PermissionTarget.PermissionTypeCategory subCategory : subCategories) {
                if (subCategory.getCategoryName().equalsIgnoreCase(identifier)) {
                    return subCategory;
                }

                PermissionTarget.PermissionTypeCategory subcategory = findSubcategory(identifier, subCategory);

                if (subcategory != null && foundInLowerCategory == null) {
                    foundInLowerCategory = subCategory;
                }
            }

            return foundInLowerCategory;
        }

        return null;
    }

    private Optional<? extends PermissionTarget> findRootTarget(String identifier) {
        for (PermissionTarget.TargetType targetType : PermissionTarget.TargetType.values()) {
            Optional<? extends PermissionTarget> found = targetType.findPermissionTarget(identifier);

            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    private Optional<PermissionTarget.PermissionTypeCategory> getRootCategory(String category) {
        for (PermissionTarget.TargetType targetType : PermissionTarget.TargetType.values()) {
            if (targetType.getCategoryName().equalsIgnoreCase(category)) {
                return Optional.of(targetType);
            }
        }

        return Optional.empty();
    }

    @Nullable
    private PermissionTarget.PermissionTypeCategory getSubCategory(String category, PermissionTarget.PermissionTypeCategory current) {
        PermissionTarget.PermissionTypeCategory[] subCategories = current.getSubCategories();
        if (subCategories == null) {
            return null;
        }

        for (PermissionTarget.PermissionTypeCategory subCategory : subCategories) {
            if (subCategory.getCategoryName().equalsIgnoreCase(category)) {
                return subCategory;
            }
        }

        return null;
    }

    private PermissionTarget requirePermissionTarget(String identifier) {
        return getPermissionTarget(identifier).orElseThrow(() -> new NoResultsFoundException(String.format("No such permission target '%s'", identifier)));
    }

}
