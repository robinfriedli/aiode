package net.robinfriedli.botify.command.commands;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.LinkedHashMultimap;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Role;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.stringlist.StringListImpl;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class HelpCommand extends AbstractCommand {

    public HelpCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        if (getCommandBody().isBlank()) {
            listCommands();
        } else {
            showCommandHelp();
        }
    }

    private void showCommandHelp() {
        getManager().getCommand(getContext(), getCommandBody()).ifPresentOrElse(command -> {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Command " + command.getIdentifier() + ":");
            embedBuilder.setDescription(command.getDescription());
            Guild guild = getContext().getGuild();
            AccessConfiguration accessConfiguration = Botify.get().getGuildManager().getAccessConfiguration(command.getIdentifier(), guild);
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
                embedBuilder.addField("__Arguments__", "Keywords that alter the command behavior or define a search scope", false);

                for (ArgumentContribution.Argument argument : argumentContribution.getArguments()) {
                    embedBuilder.addField("$" + argument.getArgument(), argument.getDescription(), false);
                }

            }

            List<XmlElement> examples = command.getCommandContribution().query(tagName("example")).collect();
            if (!examples.isEmpty()) {
                embedBuilder.addField("__Examples__", "Practical usage examples for this command. Note that '$botify' can be exchanged for your custom prefix or bot name.", false);
                for (XmlElement example : examples) {
                    embedBuilder.addField(example.getAttribute("title").getValue(), example.getTextContent(), false);
                }
            }

            sendMessage(embedBuilder);
        }, () -> {
            throw new InvalidCommandException("No command found for " + getCommandBody());
        });
    }

    private void listCommands() {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("**Commands:**");
        embedBuilder.appendDescription("To get help with a specific command just enter the name of the command. E.g. $botify help play.");

        List<AbstractCommand> commands = getManager().getAllCommands(getContext());
        LinkedHashMultimap<Category, AbstractCommand> commandsByCategory = LinkedHashMultimap.create();
        for (AbstractCommand command : commands) {
            Category category = command.getCategory();
            if (category != Category.ADMIN) {
                commandsByCategory.put(category, command);
            }
        }

        List<Category> categories = commandsByCategory.keySet().stream().sorted(Comparator.comparingInt(Enum::ordinal)).collect(Collectors.toList());
        for (Category category : categories) {
            Iterator<AbstractCommand> commandIterator = commandsByCategory.get(category).iterator();
            StringBuilder commandString = new StringBuilder();
            for (int i = 0; commandIterator.hasNext(); i++) {
                AbstractCommand c = commandIterator.next();
                commandString.append(c.getIdentifier());

                if (commandIterator.hasNext()) {
                    commandString.append(System.lineSeparator());
                }
            }
            embedBuilder.addField(category.getName(), commandString.toString(), true);
        }

        sendMessage(embedBuilder);
    }

    @Override
    public void onSuccess() {
    }
}
