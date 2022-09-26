package net.robinfriedli.aiode.command.commands.customisation;

import java.util.Objects;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;

public class PrefixCommand extends AbstractCommand {

    public PrefixCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        SpringPropertiesConfig springPropertiesConfig = Aiode.get().getSpringPropertiesConfig();
        boolean messageContentEnabled = Objects.requireNonNullElse(springPropertiesConfig.getApplicationProperty(Boolean.class, "aiode.preferences.enable_message_content"), true);

        if (!messageContentEnabled) {
            String ping = getContext().getGuild().getSelfMember().getAsMention();
            throw new InvalidCommandException(String.format("Message content has been disabled, custom prefixes do not work. Ping the bot with %s as prefix instead.", ping));
        }

        getContext().getGuildContext().setPrefix(getCommandInput());
    }

    @Override
    public void onSuccess() {
        // notification sent by GuildPropertyInterceptor
    }
}
