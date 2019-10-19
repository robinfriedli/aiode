package net.robinfriedli.botify.command.commands;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GrantedRole;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.Table2;
import net.robinfriedli.stringlist.StringListImpl;
import org.hibernate.Session;

public class PermissionCommand extends AbstractCommand {

    private final Splitter commaSplitter = Splitter.on(",").trimResults().omitEmptyStrings();

    public PermissionCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        if (argumentSet("grant")) {
            grantPermissions();
        } else if (argumentSet("deny")) {
            denyPermissions();
        } else if (argumentSet("clear")) {
            clearCommands();
        } else if (argumentSet("lock")) {
            lockCommands();
        } else {
            listPermissions();
        }
    }

    private void listPermissions() {
        GuildSpecification specification = getContext().getGuildContext().getSpecification();
        EmbedBuilder embedBuilder = new EmbedBuilder();

        List<CommandContribution> commandContributions = getManager().getAllCommands(getContext()).stream()
            .filter(command -> !command.getCategory().getName().equals("admin"))
            .map(AbstractCommand::getCommandContribution)
            .collect(Collectors.toList());

        Table2 permissionTable = new Table2(embedBuilder);
        permissionTable.addColumn("Command", commandContributions, CommandContribution::getIdentifier);
        permissionTable.addColumn("Available to", commandContributions, commandContribution -> {
            Optional<AccessConfiguration> accessConfiguration = specification.getAccessConfiguration(commandContribution.getIdentifier());
            if (accessConfiguration.isPresent()) {
                AccessConfiguration ac = accessConfiguration.get();
                List<Role> roles = ac.getRoles(getContext().getGuild());
                if (roles.isEmpty()) {
                    return "Guild owner and administrator roles only";
                } else {
                    return StringListImpl.create(roles, Role::getName).toSeparatedString(", ");
                }
            } else {
                return "Available to everyone";
            }
        });
        permissionTable.build();
        sendMessage(embedBuilder);
    }

    private void grantPermissions() {
        GuildSpecification specification = getContext().getGuildContext().getSpecification();
        Session session = getContext().getSession();
        Set<CommandContribution> selectedCommands;
        Set<Role> selectedRoles;
        if (argumentSet("all")) {
            selectedCommands = getAllCommands();
            selectedRoles = getSelectedRoles(getCommandInput());
        } else {
            selectedCommands = getSelectedCommands(getCommandInput());
            selectedRoles = getSelectedRoles(getArgumentValue("to"));
        }

        boolean addedAnything = invoke(() -> {
            boolean takenAction = false;
            for (CommandContribution selectedCommand : selectedCommands) {
                Optional<AccessConfiguration> existingConfiguration = specification.getAccessConfiguration(selectedCommand.getIdentifier());
                if (existingConfiguration.isPresent()) {
                    AccessConfiguration accessConfiguration = existingConfiguration.get();
                    Set<String> roleIds = accessConfiguration.getRoleIds();
                    for (Role selectedRole : selectedRoles) {
                        if (roleIds.contains(selectedRole.getId())) {
                            continue;
                        }

                        GrantedRole role = new GrantedRole(selectedRole);
                        session.persist(role);
                        accessConfiguration.addRole(role);
                        takenAction = true;
                    }
                } else {
                    AccessConfiguration accessConfiguration = new AccessConfiguration(selectedCommand.getIdentifier());
                    for (Role selectedRole : selectedRoles) {
                        GrantedRole role = new GrantedRole(selectedRole);
                        session.persist(role);
                        accessConfiguration.addRole(role);
                    }

                    session.persist(accessConfiguration);
                    specification.addAccessConfiguration(accessConfiguration);
                    takenAction = true;
                }

            }
            return takenAction;
        });

        if (!addedAnything) {
            sendError("All selected roles have already been granted each selected command. No changes needed.");
        }
    }

    private void denyPermissions() {
        GuildSpecification specification = getContext().getGuildContext().getSpecification();
        Set<CommandContribution> selectedCommands;
        Set<Role> selectedRoles;
        if (argumentSet("all")) {
            selectedCommands = getAllCommands();
            selectedRoles = getSelectedRoles(getCommandInput());
        } else {
            selectedCommands = getSelectedCommands(getCommandInput());
            selectedRoles = getSelectedRoles(getArgumentValue("for"));
        }
        Session session = getContext().getSession();

        boolean removedAnything = invoke(() -> {
            boolean takenAction = false;
            for (CommandContribution selectedCommand : selectedCommands) {
                Optional<AccessConfiguration> existingAccessConfiguration = specification.getAccessConfiguration(selectedCommand.getIdentifier());
                if (existingAccessConfiguration.isPresent()) {
                    AccessConfiguration accessConfiguration = existingAccessConfiguration.get();

                    for (Role selectedRole : selectedRoles) {
                        Optional<GrantedRole> setRole = accessConfiguration.getRole(selectedRole.getId());
                        if (setRole.isPresent()) {
                            GrantedRole grantedRole = setRole.get();
                            accessConfiguration.removeRole(grantedRole);
                            session.delete(grantedRole);
                            takenAction = true;
                        }
                    }
                }
            }

            return takenAction;
        });

        if (!removedAnything) {
            sendError("None of the selected roles were granted any of the selected commands. The deny argument is used " +
                "to remove granted roles from commands.");
        }
    }

    private void clearCommands() {
        GuildSpecification specification = getContext().getGuildContext().getSpecification();
        Set<CommandContribution> selectedCommands = getSelectedCommands(getCommandInput());
        Session session = getContext().getSession();

        boolean removedAnything = invoke(() -> {
            boolean takenAction = false;
            for (CommandContribution selectedCommand : selectedCommands) {
                Optional<AccessConfiguration> accessConfiguration = specification.getAccessConfiguration(selectedCommand.getIdentifier());
                if (accessConfiguration.isPresent()) {
                    AccessConfiguration ac = accessConfiguration.get();
                    takenAction = specification.removeAccessConfiguration(ac);
                    ac.getRoles().forEach(session::delete);
                    session.delete(ac);
                }
            }

            return takenAction;
        });

        if (!removedAnything) {
            sendError("None of the selected commands had an access configuration set up to begin with. No action required.");
        }
    }

    private void lockCommands() {
        GuildSpecification specification = getContext().getGuildContext().getSpecification();
        Set<CommandContribution> selectedCommands = getSelectedCommands(getCommandInput());
        Session session = getContext().getSession();

        boolean lockedAnything = invoke(() -> {
            boolean takenAction = false;
            for (CommandContribution selectedCommand : selectedCommands) {
                Optional<AccessConfiguration> existingAccessConfiguration = specification.getAccessConfiguration(selectedCommand.getIdentifier());
                if (existingAccessConfiguration.isPresent()) {
                    AccessConfiguration accessConfiguration = existingAccessConfiguration.get();
                    Set<GrantedRole> setRoles = accessConfiguration.getRoles();
                    for (GrantedRole setRole : setRoles) {
                        accessConfiguration.removeRole(setRole);
                        session.delete(setRole);
                        takenAction = true;
                    }
                } else {
                    AccessConfiguration accessConfiguration = new AccessConfiguration(selectedCommand.getIdentifier());
                    session.persist(accessConfiguration);
                    specification.addAccessConfiguration(accessConfiguration);
                    takenAction = true;
                }
            }

            return takenAction;
        });

        if (!lockedAnything) {
            sendError("All selected commands are already only available to the guild owner and administrator roles");
        }
    }

    private Set<Role> getSelectedRoles(String roleString) {
        Guild guild = getContext().getGuild();
        Set<Role> selectedRoles = Sets.newHashSet();
        List<String> roles = commaSplitter.splitToList(roleString);

        for (String role : roles) {
            List<Role> rolesByName = guild.getRolesByName(role, true);
            if (rolesByName.isEmpty()) {
                throw new InvalidCommandException("No such role " + role);
            }
            selectedRoles.addAll(rolesByName);
        }

        return selectedRoles;
    }

    private Set<CommandContribution> getSelectedCommands(String commandString) {
        if (argumentSet("all")) {
            return getAllCommands();
        } else if (argumentSet("category")) {
            Set<CommandContribution> selectedCommands = getManager()
                .getAllCommands(getContext())
                .stream()
                .filter(command -> command.getCategory().getName().equalsIgnoreCase(commandString))
                .map(AbstractCommand::getCommandContribution)
                .collect(Collectors.toSet());

            if (selectedCommands.isEmpty()) {
                throw new InvalidCommandException("Category " + commandString + " does not exist");
            }

            return selectedCommands;
        } else {
            Set<CommandContribution> selectedCommands = Sets.newHashSet();
            for (String identifier : commaSplitter.splitToList(commandString)) {
                CommandContribution commandContribution = getManager().getCommandContribution(identifier);
                if (commandContribution == null) {
                    throw new InvalidCommandException("So such command " + identifier);
                }
                selectedCommands.add(commandContribution);
            }

            return selectedCommands;
        }
    }

    private Set<CommandContribution> getAllCommands() {
        return getManager().getAllCommands(getContext()).stream()
            .filter(c -> c.getCategory() != Category.ADMIN)
            .map(AbstractCommand::getCommandContribution)
            .collect(Collectors.toSet());
    }

    @Override
    public void onSuccess() {
        // success message sent by interceptor
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("to").needsArguments("grant")
            .setDescription("Specify the roles to grant access for.");
        argumentContribution.map("for").excludesArguments("to").needsArguments("deny")
            .setDescription("Specify the roles to remove access privileges from when using the $deny argument.");
        argumentContribution.map("grant").setRequiresInput(true).excludesArguments("deny")
            .setDescription("Grant the selected commands to the selected roles. Limits the command to the selected roles " +
                "if no roles were previously defined.");
        argumentContribution.map("deny").setRequiresInput(true).excludesArguments("grant")
            .setDescription("Removes a set role from a command.");
        argumentContribution.map("clear").excludesArguments("grant", "deny")
            .setDescription("Clear all restrictions for a command to make it available for everyone.");
        argumentContribution.map("category").setRequiresInput(true)
            .setDescription("Manage a category of commands. Use the help command to show all categories.");
        argumentContribution.map("all").excludesArguments("category")
            .setDescription("Manage all commands at once. The inline argument $for is not required with this argument.");
        argumentContribution.map("lock").excludesArguments("grant", "deny", "clear")
            .setDescription("Make the selected commands available only to the guild owner.");
        return argumentContribution;
    }

}
