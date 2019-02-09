package net.robinfriedli.botify.command.commands;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.Table;
import net.robinfriedli.stringlist.StringListImpl;

public class HelpCommand extends AbstractCommand {

    public HelpCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, false, false, false, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        if (getCommandBody().isBlank()) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setColor(Color.GREEN);
            embedBuilder.setTitle("Commands:");
            embedBuilder.appendDescription("To get help with a specific command just enter the name of the command.");

            List<AbstractCommand> commands = getManager().getAllCommands(getContext());
            Multimap<Category, AbstractCommand> commandsByCategory = HashMultimap.create();
            for (AbstractCommand command : commands) {
                commandsByCategory.put(command.getCategory(), command);
            }

            List<Category> categories = commandsByCategory.keySet().stream().sorted(Comparator.comparingInt(Enum::ordinal)).collect(Collectors.toList());
            for (Category category : categories) {
                embedBuilder.addField("__" + category.getName() + "__", "", false);
                for (AbstractCommand command : commandsByCategory.get(category)) {
                    embedBuilder.addField(command.getIdentifier(), command.getDescription(), false);
                }
            }

            sendMessage(getContext().getChannel(), embedBuilder.build());
        } else {
            getManager().getCommand(getContext(), getCommandBody()).ifPresentOrElse(command -> {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("Command " + command.getIdentifier() + ":");
                embedBuilder.setDescription(command.getDescription());
                Guild guild = getContext().getGuild();
                AccessConfiguration accessConfiguration = getManager().getGuildManager().getAccessConfiguration(command.getIdentifier(), guild);
                if (accessConfiguration != null) {
                    String title = "Available to roles: ";
                    String text;
                    List<Role> roles = accessConfiguration.getRoles(guild);
                    if (!roles.isEmpty()) {
                        text = StringListImpl.create(roles, Role::getName).toSeparatedString(", ");
                    } else {
                        text = "Guild owner only";
                    }

                    embedBuilder.addField(title, text, false);
                }
                ArgumentContribution argumentContribution = command.setupArguments();
                if (!argumentContribution.isEmpty()) {
                    embedBuilder.addField("__Arguments__", "", false);
                    Table table = Table.create(50, 1, false, "", "", "", "=");
                    table.setTableHead(table.createCell("Argument", 12), table.createCell("Description"));

                    for (ArgumentContribution.Argument argument : argumentContribution.getArguments()) {
                        embedBuilder.addField("$" + argument.getArgument(), argument.getDescription(), false);
                    }

                }

                sendMessage(getContext().getChannel(), embedBuilder.build());
            }, () -> {
                throw new InvalidCommandException("No command found for " + getCommandBody());
            });
        }
    }

    @Override
    public void onSuccess() {
    }
}
