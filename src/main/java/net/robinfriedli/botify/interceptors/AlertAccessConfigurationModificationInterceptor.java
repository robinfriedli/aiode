package net.robinfriedli.botify.interceptors;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.AccessConfiguration;
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

        String s = builder.toString();
        String message = s.length() > 2000 ? "Adjusted access configurations" : s;
        if (!message.isEmpty()) {
            messageService.sendSuccess(message, context.getChannel());
        }
    }

    private void handleCreatedAccessConfigurations(List<AccessConfiguration> createdAccessConfigurations,
                                                   List<GrantedRole> createdGrantedRoles,
                                                   StringBuilder builder) {
        Set<String> grantedCommands = createdAccessConfigurations.stream()
            .map(AccessConfiguration::getCommandIdentifier)
            .collect(Collectors.toSet());
        Set<String> grantedRoles = createdGrantedRoles.stream()
            .map(role -> role.getRole(context.getGuild()).getName())
            .collect(Collectors.toSet());

        String commandString = String.join(", ", grantedCommands);
        String roleString = String.join(", ", grantedRoles);

        if (createdAccessConfigurations.size() == 1) {
            builder.append("Command '").append(commandString).append("' is now limited to ");
        } else {
            builder.append("Commands [").append(commandString).append("] are now limited to ");
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

        Set<String> clearedCommands = deletedAccessConfigurations.stream()
            .map(AccessConfiguration::getCommandIdentifier)
            .collect(Collectors.toSet());
        String commandString = String.join(", ", clearedCommands);

        if (deletedAccessConfigurations.size() == 1) {
            builder.append("Command '").append(commandString).append("'");
        } else {
            builder.append("Commands [").append(commandString).append("]");
        }
        builder.append(" can now be used by everyone");
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
                    builder.append("Role '").append(roleString).append("' is");
                } else {
                    builder.append("Roles [").append(roleString).append("] are");
                }

                if (added) {
                    builder.append(" now ");
                } else {
                    builder.append(" no longer ");
                }
                builder.append("allowed to use command '").append(accessConfiguration.getCommandIdentifier()).append("'");
            }
        }
    }

}
