package net.robinfriedli.botify.command.commands.customisation;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class PrefixCommand extends AbstractCommand {

    public PrefixCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description, Category.CUSTOMISATION);
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
