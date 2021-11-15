package net.robinfriedli.aiode.command.commands.customisation;

import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;

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
