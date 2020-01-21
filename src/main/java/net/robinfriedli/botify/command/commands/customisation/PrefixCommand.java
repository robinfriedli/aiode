package net.robinfriedli.botify.command.commands.customisation;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class PrefixCommand extends AbstractCommand {

    public PrefixCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        getContext().getGuildContext().setPrefix(getCommandInput());
    }

    @Override
    public void onSuccess() {
        // notification sent by GuildPropertyInterceptor
    }
}
