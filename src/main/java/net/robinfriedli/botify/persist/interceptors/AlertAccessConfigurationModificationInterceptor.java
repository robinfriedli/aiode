package net.robinfriedli.botify.persist.interceptors;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.PermissionTarget;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.CustomPermissionTarget;
import net.robinfriedli.botify.entities.GrantedRole;
import org.hibernate.Interceptor;

public class AlertAccessConfigurationModificationInterceptor extends CollectingInterceptor {

    private final CommandContext context;
    private final MessageService messageService;

    public AlertAccessConfigurationModificationInterceptor(Interceptor next, Logger logger, CommandContext commandContext, MessageService messageService) {
        super(next, logger);
        context = commandContext;
        this.messageService = messageService;
    }

    @Override
    public void afterCommit() {
        List<AccessConfiguration> createdAccessConfigurations = getCreatedEntities(AccessConfiguration.class);
        List<AccessConfiguration> deletedAccessConfigurations = getDeletedEntities(AccessConfiguration.class);
        List<GrantedRole> createdGrantedRoles = getCreatedEntities(GrantedRole.class);
        List<GrantedRole> deletedGrantedRoles = getDeletedEntities(GrantedRole.class);
        List<CustomPermissionTarget> createdPermissionTargets = getCreatedEntities(CustomPermissionTarget.class);
        List<CustomPermissionTarget> deletedPermissionTargets = getDeletedEntities(CustomPermissionTarget.class);

        StringBuilder builder = new StringBuilder();

        if (!createdAccessConfigurations.isEmpty()) {
            handleCreatedAccessConfigurations(createdAccessConfigurations, createdGrantedRoles, builder);
        }

        if (!deletedAccessConfigurations.isEmpty()) {
            handleDeletedAccessConfigurations(deletedAccessConfigurations, builder);
        }

        if (!createdGrantedRoles.isEmpty()) {
            handleModifiedAccessConfiguration(createdAccessConfigurations, createdGrantedRoles, true, builder);
        }

        if (!deletedGrantedRoles.isEmpty()) {
            handleModifiedAccessConfiguration(deletedAccessConfigurations, deletedGrantedRoles, false, builder);
        }

        if (!createdPermissionTargets.isEmpty()) {
            alertPermissionTargetModification("Created", builder, createdPermissionTargets);
        }

        if (!deletedPermissionTargets.isEmpty()) {
            alertPermissionTargetModification("Deleted", builder, deletedPermissionTargets);
        }

        String s = builder.toString();
        String message = s.length() > 2000 ? "Adjusted access configurations" : s;
        if (!message.isEmpty()) {
            messageService.sendSuccess(message, context.getChannel());
        }
    }

    private void handleCreatedAccessConfigurations(List<AccessConfiguration> createdAccessConfigurations,
                                                   List<GrantedRole> createdGrantedRoles,
                                                   StringBuilder builder) {
        Set<String> grantedPermissions = createdAccessConfigurations.stream()
            .map(AccessConfiguration::getPermissionIdentifier)
            .collect(Collectors.toSet());
        Set<String> grantedRoles = createdGrantedRoles.stream()
            .map(role -> role.getRole(context.getGuild()).getName())
            .collect(Collectors.toSet());

        String permissionString = String.join(", ", grantedPermissions);
        String roleString = String.join(", ", grantedRoles);
        String prefix = getPermissionTypePrefix(createdAccessConfigurations, true);

        if (createdAccessConfigurations.size() == 1) {
            builder.append(prefix).append(" '").append(permissionString).append("' is now limited to ");
        } else {
            builder.append(prefix).append("s [").append(permissionString).append("] are now limited to ");
        }

        if (grantedRoles.size() == 1) {
            builder.append("role '").append(roleString).append("'");
        } else if (grantedRoles.isEmpty()) {
            builder.append("guild owner and administrator roles");
        } else {
            builder.append("roles [").append(roleString).append("]");
        }
    }

    private void handleDeletedAccessConfigurations(List<AccessConfiguration> deletedAccessConfigurations, StringBuilder builder) {
        if (!builder.toString().isEmpty()) {
            builder.append(System.lineSeparator());
        }

        String prefix = getPermissionTypePrefix(deletedAccessConfigurations, true);

        Set<String> clearedCommands = deletedAccessConfigurations.stream()
            .map(AccessConfiguration::getPermissionIdentifier)
            .collect(Collectors.toSet());
        String commandString = String.join(", ", clearedCommands);

        if (deletedAccessConfigurations.size() == 1) {
            builder.append(prefix).append(" '").append(commandString).append("'");
        } else {
            builder.append(prefix).append("s [").append(commandString).append("]");
        }

        builder.append(" can now be accessed by everyone");
    }

    private void handleModifiedAccessConfiguration(List<AccessConfiguration> affectedAccessConfigurations,
                                                   List<GrantedRole> affectedGrantedRoles,
                                                   boolean added,
                                                   StringBuilder builder) {
        Multimap<AccessConfiguration, GrantedRole> grantedRoleMap = HashMultimap.create();
        for (GrantedRole grantedRole : affectedGrantedRoles) {
            grantedRoleMap.put(grantedRole.getAccessConfiguration(), grantedRole);
        }

        for (AccessConfiguration accessConfiguration : grantedRoleMap.keySet()) {
            if (!affectedAccessConfigurations.contains(accessConfiguration)) {
                if (!builder.toString().isEmpty()) {
                    builder.append(System.lineSeparator());
                }

                Set<String> grantedRoles = grantedRoleMap.get(accessConfiguration).stream()
                    .map(role -> role.getRole(context.getGuild()).getName())
                    .collect(Collectors.toSet());
                String roleString = String.join(", ", grantedRoles);
                if (grantedRoles.size() == 1) {
                    builder.append("Role '").append(roleString).append("'");
                } else {
                    builder.append("Roles [").append(roleString).append("]");
                }

                if (added) {
                    builder.append(" now ");
                } else {
                    builder.append(" no longer ");
                }

                builder.append(grantedRoles.size() == 1 ? "has " : "have ")
                    .append("access to ")
                    .append(accessConfiguration.getPermissionType().asEnum().getName())
                    .append(" '")
                    .append(accessConfiguration.getPermissionIdentifier())
                    .append("'");
            }
        }
    }

    private void alertPermissionTargetModification(String verb, StringBuilder builder, List<CustomPermissionTarget> affectedEntities) {
        if (!affectedEntities.isEmpty()) {
            if (!builder.toString().isEmpty()) {
                builder.append(System.lineSeparator());
            }

            if (affectedEntities.size() == 1) {
                CustomPermissionTarget permissionTarget = affectedEntities.get(0);
                builder.append(String.format("%s permission target '%s'", verb, permissionTarget.getFullPermissionTargetIdentifier()));
            } else {
                builder.append(String.format("%s %d permission targets", verb, affectedEntities.size()));
            }
        }
    }

    private String getPermissionTypePrefix(List<AccessConfiguration> accessConfigurations, boolean capitalize) {
        boolean allOfSameType = true;
        PermissionTarget.TargetType type = null;
        for (AccessConfiguration deletedAccessConfiguration : accessConfigurations) {
            PermissionTarget.TargetType targetType = deletedAccessConfiguration.getPermissionType().asEnum();
            if (type != null && targetType != type) {
                allOfSameType = false;
                break;
            } else if (type == null) {
                type = targetType;
            }
        }

        String prefix;
        if (allOfSameType) {
            // type is not null since deletedAccessConfigurations is not empty
            //noinspection ConstantConditions
            String typeName = type.getName();
            if (capitalize) {
                if (typeName.length() > 1) {
                    prefix = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                } else {
                    prefix = typeName.toUpperCase();
                }
            } else {
                prefix = typeName;
            }
        } else if (capitalize) {
            prefix = "Permission";
        } else {
            prefix = "permission";
        }

        return prefix;
    }

}
