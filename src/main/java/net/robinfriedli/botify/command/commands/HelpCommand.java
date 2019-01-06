package net.robinfriedli.botify.command.commands;

import java.util.List;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.AlertService;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.Table;
import net.robinfriedli.stringlist.StringListImpl;

public class HelpCommand extends AbstractCommand {

    public HelpCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier) {
        super(context, commandManager, commandString, false, false, false, identifier,
            "Lists all available commands and their descriptions or provides help with a specific command.", Category.GENERAL);
    }

    @Override
    public void doRun() {
        AlertService alertService = new AlertService();
        if (getCommandBody().isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append("To get help with a specific command just enter the name of the command.").append(System.lineSeparator());
            sb.append("Available commands:").append(System.lineSeparator());

            List<AbstractCommand> commands = getManager().getAllCommands(getContext());
            Multimap<Category, AbstractCommand> commandsByCategory = HashMultimap.create();
            for (AbstractCommand command : commands) {
                commandsByCategory.put(command.getCategory(), command);
            }
            for (Category category : commandsByCategory.keySet()) {
                sb.append("__**").append(category.getName()).append("**__").append(System.lineSeparator());
                for (AbstractCommand command : commandsByCategory.get(category)) {
                    sb.append("**").append(command.getIdentifier()).append("**").append(System.lineSeparator());
                    sb.append(command.getDescription()).append(System.lineSeparator());
                }
                sb.append(System.lineSeparator());
            }

            sendMessage(getContext().getChannel(), sb.toString());
        } else {
            getManager().getCommand(getContext(), getCommandBody()).ifPresentOrElse(command -> {
                StringBuilder sb = new StringBuilder();
                sb.append(command.getDescription());
                Guild guild = getContext().getGuild();
                AccessConfiguration accessConfiguration = getManager().getGuildManager().getAccessConfiguration(command.getIdentifier(), guild);
                if (accessConfiguration != null) {
                    sb.append(System.lineSeparator()).append("-".repeat(50)).append(System.lineSeparator());
                    sb.append("Available to roles: ");
                    List<Role> roles = accessConfiguration.getRoles(guild);
                    if (!roles.isEmpty()) {
                        sb.append(StringListImpl.create(roles, Role::getName));
                    } else {
                        sb.append("Guild owner only");
                    }
                }
                ArgumentContribution argumentContribution = command.setupArguments();
                if (!argumentContribution.isEmpty()) {
                    Table table = Table.create(50, 1, false, "", "", "", "=");
                    table.setTableHead(table.createCell("Argument", 12), table.createCell("Description"));

                    for (ArgumentContribution.Argument argument : argumentContribution.getArguments()) {
                        table.addRow(table.createCell("$" + argument.getArgument(), 12), table.createCell(argument.getDescription()));
                    }

                    sb.append(System.lineSeparator()).append("-".repeat(50));
                    sb.append(System.lineSeparator()).append(table.normalize());
                }

                alertService.sendWrapped(sb.toString(), "```", getContext().getChannel());
            }, () -> {
                throw new InvalidCommandException("No command found for " + getCommandBody());
            });
        }
    }

    @Override
    public void onSuccess() {
    }
}
