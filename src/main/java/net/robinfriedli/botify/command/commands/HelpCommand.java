package net.robinfriedli.botify.command.commands;

import java.util.List;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.AlertService;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.Table;

public class HelpCommand extends AbstractCommand {

    public HelpCommand(CommandContext context, CommandManager commandManager, String commandString) {
        super(context, commandManager, commandString, false, false, false, "");
    }

    @Override
    public void doRun() throws Exception {
        AlertService alertService = new AlertService();
        if (getCommandBody().isBlank()) {
            StringBuilder sb = new StringBuilder();
            sb.append("To get help with a specific command just enter the name of the command.").append(System.lineSeparator());
            sb.append("Available commands:").append(System.lineSeparator());

            List<AbstractCommand> commands = getManager().getAllCommands(getContext());
            for (AbstractCommand command : commands) {
                if (command instanceof HelpCommand) {
                    continue;
                }

                sb.append("**").append(command.getName()).append("**").append(System.lineSeparator());
                sb.append(command.getDescription()).append(System.lineSeparator());
            }

            sendMessage(getContext().getChannel(), sb.toString());
        } else {
            getManager().getCommand(getContext(), getCommandBody()).ifPresentOrElse(command -> {
                StringBuilder sb = new StringBuilder();
                sb.append(command.getDescription());
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
