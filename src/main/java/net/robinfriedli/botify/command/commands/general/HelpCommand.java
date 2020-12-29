package net.robinfriedli.botify.command.commands.general;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.argument.ArgumentController;
import net.robinfriedli.botify.command.argument.CommandArgument;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.discord.property.properties.PrefixProperty;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.stringlist.StringList;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class HelpCommand extends AbstractCommand {

    public HelpCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
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
                prefix = PrefixProperty.DEFAULT_FALLBACK + " ";
            }
            char argumentPrefix = ArgumentPrefixProperty.getForCurrentContext().getArgumentPrefix();

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Command " + command.getIdentifier() + ":");
            String descriptionFormat = command.getDescription();
            String descriptionText = String.format(descriptionFormat, prefix, argumentPrefix);
            embedBuilder.setDescription(descriptionText);
            Guild guild = getContext().getGuild();
            Optional<AccessConfiguration> accessConfiguration = Botify.get().getSecurityManager().getAccessConfiguration(command.getPermissionTarget(), guild);
            if (accessConfiguration.isPresent()) {
                String title = "Available to roles: ";
                String text;
                List<Role> roles = accessConfiguration.get().getRoles(guild);
                if (!roles.isEmpty()) {
                    text = StringList.create(roles, Role::getName).toSeparatedString(", ");
                } else {
                    text = "Guild owner and administrator roles only";
                }

                embedBuilder.addField(title, text, false);
            }
            ArgumentController argumentController = command.getArgumentController();
            if (argumentController.hasArguments()) {
                embedBuilder.addField("__Arguments__", "Keywords that alter the command behavior or define a search scope.", false);

                argumentController
                    .getArguments()
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(CommandArgument::getIdentifier))
                    .forEach(argument ->
                        embedBuilder.addField(
                            argumentPrefix + argument.getIdentifier(),
                            String.format(argument.getDescription(), prefix, argumentPrefix),
                            false
                        )
                    );
            }

            List<XmlElement> examples = command.getCommandContribution().query(tagName("example")).collect();
            if (!examples.isEmpty()) {
                embedBuilder.addField("__Examples__", "Practical usage examples for this command.", false);
                for (XmlElement example : examples) {
                    String exampleText = String.format(example.getTextContent(), prefix, argumentPrefix);
                    String titleText = String.format(example.getAttribute("title").getValue(), prefix, argumentPrefix);
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
        String prefix = PrefixProperty.getEffectiveCommandStartForCurrentContext();
        embedBuilder.appendDescription(String.format("To get help with a specific command just enter the name of the command. E.g. %shelp play.", prefix));

        List<MessageEmbed.Field> fields = Lists.newArrayList();
        for (Category category : Category.values()) {
            MessageEmbed.Field embedField = category.createEmbedField();

            if (embedField != null) {
                fields.add(embedField);
            }
        }

        for (MessageEmbed.Field field : fields) {
            embedBuilder.addField(field);
        }

        sendMessage(embedBuilder);
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public boolean isPrivileged() {
        return true;
    }
}
