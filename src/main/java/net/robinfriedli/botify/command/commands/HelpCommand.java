package net.robinfriedli.botify.command.commands;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.google.common.collect.LinkedHashMultimap;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GuildSpecification;
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
        if (getCommandInput().isBlank()) {
            listCommands();
        } else {
            showCommandHelp();
        }
    }

    private void showCommandHelp() {
        getManager().getCommand(getContext(), getCommandInput()).ifPresentOrElse(command -> {
            String prefix;
            GuildSpecification specification = getContext().getGuildContext().getSpecification();
            String setPrefix = specification.getPrefix();
            String botName = specification.getBotName();
            if (!Strings.isNullOrEmpty(setPrefix)) {
                prefix = setPrefix;
            } else if (!Strings.isNullOrEmpty(botName)) {
                prefix = botName + " ";
            } else {
                prefix = "$botify ";
            }

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Command " + command.getIdentifier() + ":");
            String descriptionFormat = command.getDescription();
            String descriptionText = descriptionFormat.contains("%s") ? String.format(descriptionFormat, prefix) : descriptionFormat;
            embedBuilder.setDescription(descriptionText);
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
                embedBuilder.addField("__Arguments__", "Keywords that alter the command behavior or define a search scope.", false);

                char argumentPrefix = ArgumentPrefixProperty.getForCurrentContext();
                for (ArgumentContribution.Argument argument : argumentContribution.getArguments()) {
                    embedBuilder.addField(argumentPrefix + argument.getIdentifier(), argument.getDescription(), false);
                }

            }

            List<XmlElement> examples = command.getCommandContribution().query(tagName("example")).collect();
            if (!examples.isEmpty()) {
                embedBuilder.addField("__Examples__", "Practical usage examples for this command.", false);
                for (XmlElement example : examples) {
                    String exampleFormat = example.getTextContent();
                    String exampleText = exampleFormat.contains("%s") ? String.format(exampleFormat, prefix) : exampleFormat;
                    String titleFormat = example.getAttribute("title").getValue();
                    String titleText = titleFormat.contains("%s") ? String.format(titleFormat, prefix) : titleFormat;
                    embedBuilder.addField(titleText, exampleText, false);
                }
            }

            sendMessage(embedBuilder);
        }, () -> {
            throw new InvalidCommandException(String.format("No command found for '%s'", getCommandInput()));
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
            while (commandIterator.hasNext()) {
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
