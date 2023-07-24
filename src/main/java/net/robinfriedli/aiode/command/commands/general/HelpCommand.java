package net.robinfriedli.aiode.command.commands.general;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.argument.ArgumentController;
import net.robinfriedli.aiode.command.argument.CommandArgument;
import net.robinfriedli.aiode.command.widget.widgets.PaginatedMessageEmbedWidget;
import net.robinfriedli.aiode.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.aiode.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.aiode.discord.property.properties.PrefixProperty;
import net.robinfriedli.aiode.entities.AccessConfiguration;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
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
            SpringPropertiesConfig springPropertiesConfig = Aiode.get().getSpringPropertiesConfig();
            boolean messageContentEnabled = Objects.requireNonNullElse(springPropertiesConfig.getApplicationProperty(Boolean.class, "aiode.preferences.enable_message_content"), true);

            GuildSpecification specification = getContext().getGuildContext().getSpecification();
            Guild guild = getContext().getGuild();
            String setPrefix = specification.getPrefix();
            String botName = specification.getBotName();

            String prefix;
            if (!messageContentEnabled) {
                prefix = guild.getSelfMember().getAsMention() + " ";
            } else if (!Strings.isNullOrEmpty(setPrefix)) {
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
            Optional<AccessConfiguration> accessConfiguration = Aiode.get().getSecurityManager().getAccessConfiguration(command.getPermissionTarget(), guild);
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

            embedBuilder.setColor(ColorSchemeProperty.getColor());
            PaginatedMessageEmbedWidget paginatedMessageEmbedWidget = new PaginatedMessageEmbedWidget(
                getContext().getGuildContext().getWidgetRegistry(),
                guild,
                getContext().getChannel(),
                embedBuilder.build()
            );

            paginatedMessageEmbedWidget.initialise();
        }, () -> {
            throw new InvalidCommandException(String.format("No command found for '%s'", getCommandInput()));
        });
    }

    private void listCommands() {
        SpringPropertiesConfig springPropertiesConfig = Aiode.get().getSpringPropertiesConfig();
        boolean messageContentEnabled = Objects.requireNonNullElse(springPropertiesConfig.getApplicationProperty(Boolean.class, "aiode.preferences.enable_message_content"), true);
        boolean slashCommandsEnabled = Objects.requireNonNullElse(springPropertiesConfig.getApplicationProperty(Boolean.class, "aiode.preferences.enable_slash_commands"), true);

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("**Commands:**");
        String prefix = PrefixProperty.getEffectiveCommandStartForCurrentContext();
        embedBuilder.appendDescription(String.format("To get help with a specific command just enter the name of the command. E.g. %shelp play.", prefix));
        embedBuilder.addField(
            "Text Commands",
            messageContentEnabled ? "Enabled" : "Disabled (must use @aiode mention as prefix)",
            true
        );
        embedBuilder.addField(
            "Slash Commands",
            slashCommandsEnabled ? "Enabled" : "Disabled",
            true
        );
        embedBuilder.addField(
            "Notice - Slash Commands",
            "Slash commands work along the same principle as native text commands. " +
                "However, slash command options (the counterpart to aiode arguments) do not support options without values. " +
                "Also, slash commands do not accept input that isn't mapped to an option. " +
                "That means arguments that are just a toggle (e.g. $spotify or $list) are slash command options that require an explicit boolean value (e.g. spotify:True or list:True) " +
                "and command input is set via the 'input' option.\nFor example the command `play $spotify $list my list` translates to the following slash command: `/play spotify:True list:True input:my list`",
            false
        );
        embedBuilder.addField(
            "Privacy Notice",
            "Aiode does not store any user or message data that isn't directly related to command usage. " +
                "Messages are analyzed to check if they are directed to the bot as commands (either through prefix or mention) " +
                "and are only further processed and logged if a command usage has been identified.",
            false
        );

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
