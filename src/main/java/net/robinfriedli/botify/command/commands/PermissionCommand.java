package net.robinfriedli.botify.command.commands;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.GuildSpecificationManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.stringlist.StringListImpl;

public class PermissionCommand extends AbstractCommand {

    private StringBuilder successMessageBuilder = new StringBuilder();

    public PermissionCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, true, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        GuildSpecificationManager guildManager = getManager().getGuildManager();
        if (argumentSet("grant")) {
            Pair<String, String> pair = splitInlineArgument("to");
            String commandIdentifier = pair.getLeft();
            String role = pair.getRight();

            List<Role> rolesByName = getRoles(role, guild);
            AccessConfiguration accessConfiguration = getAccessConfiguration(commandIdentifier, guild);
            if (accessConfiguration == null) {
                guildManager.registerAccessConfiguration(commandIdentifier, rolesByName, guild);
                successMessageBuilder.append("Command ").append(commandIdentifier).append(" is now limited to ")
                    .append("role: ").append(StringListImpl.create(rolesByName, Role::getName).toSeparatedString(", "));
            } else {
                List<String> roleIds
                    = Lists.newArrayList(accessConfiguration.getAttribute("roleIds").getValue().split(","));

                List<Role> rolesToSet = rolesByName.stream().filter(r -> !roleIds.contains(r.getId())).collect(Collectors.toList());
                if (rolesToSet.isEmpty()) {
                    successMessageBuilder.append("Role ").append(role).append(" is already set.");
                } else {
                    accessConfiguration.addRoles(rolesToSet);
                    successMessageBuilder.append("Role ")
                        .append(StringListImpl.create(rolesToSet, Role::getName))
                        .append(" can now access command ").append(commandIdentifier);
                }
            }
        } else if (argumentSet("deny")) {
            Pair<String, String> pair = splitInlineArgument("for");
            String commandIdentifier = pair.getLeft();
            AccessConfiguration accessConfiguration = getAccessConfiguration(commandIdentifier, guild);
            if (accessConfiguration == null) {
                throw new InvalidCommandException("No roles set for command " + getIdentifier() + ". See $botify help permission");
            }

            String role = pair.getRight();
            List<Role> rolesByName = getRoles(role, guild);

            List<String> roleIds = Lists.newArrayList(accessConfiguration.getAttribute("roleIds").getValue().split(","));
            if (rolesByName.stream().noneMatch(r -> roleIds.contains(r.getId()))) {
                throw new InvalidCommandException("Role " + role + " not set, thus cannot be removed from privileged roles.");
            }

            List<Role> rolesToRemove = rolesByName.stream().filter(r -> roleIds.contains(r.getId())).collect(Collectors.toList());
            accessConfiguration.removeRoles(rolesToRemove);
            successMessageBuilder.append("Role ")
                .append(StringListImpl.create(rolesToRemove, Role::getName).toSeparatedString(", "))
                .append(" is no longer allowed to use command ").append(commandIdentifier);
        } else if (argumentSet("clear")) {
            AccessConfiguration accessConfiguration = getAccessConfiguration(getCommandBody(), guild);
            if (accessConfiguration == null) {
                throw new InvalidCommandException("Command " + getCommandBody() + " has no restrictions.");
            }

            accessConfiguration.getContext().invoke(accessConfiguration::delete);
            successMessageBuilder.append("Command ").append(getCommandBody()).append(" is now available to everybody");
        } else {
            throw new InvalidCommandException("Either argument grant, deny or clear has to be set");
        }
    }

    @Nullable
    private AccessConfiguration getAccessConfiguration(String commandIdentifier, Guild guild) {
        CommandContribution commandContribution = getManager().getCommandContribution(commandIdentifier);
        if (commandContribution == null) {
            throw new InvalidCommandException("No such command " + commandIdentifier);
        }

        return getManager().getGuildManager().getAccessConfiguration(commandIdentifier, guild);
    }

    private List<Role> getRoles(String role, Guild guild) {
        List<Role> rolesByName = guild.getRolesByName(role, true);

        if (rolesByName.isEmpty()) {
            throw new InvalidCommandException("No roles found for " + role);
        }

        return rolesByName;
    }

    @Override
    public void onSuccess() {
        String message = successMessageBuilder.toString();
        if (!message.isEmpty()) {
            sendMessage(getContext().getChannel(), message);
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("grant").excludesArguments("deny")
            .setDescription("Add a role to a command.");
        argumentContribution.map("deny").excludesArguments("grant")
            .setDescription("Removes a set role from a command.");
        argumentContribution.map("clear")
            .setDescription("Clear all restrictions for a command to make it available for everyone.");
        return argumentContribution;
    }

}
