package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class RenameCommand extends AbstractCommand {

    private boolean couldChangeNickname;

    public RenameCommand(CommandContribution commandContribution, CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, commandContext, commandManager, commandString, true, identifier, description, Category.CUSTOMISATION);
    }

    @Override
    public void doRun() {
        GuildManager guildManager = Botify.get().getGuildManager();

        if (getCommandInput().length() < 1 || getCommandInput().length() > 20) {
            throw new InvalidCommandException("Length should be 1 - 20 characters");
        }

        couldChangeNickname = getContext().getGuildContext().setBotName(getCommandInput());
    }

    @Override
    public void onSuccess() {
        String name = getContext().getGuildContext().getSpecification().getBotName();
        if (couldChangeNickname) {
            // notification sent by GuildPropertyInterceptor
        } else {
            sendError("I do not have permission to change my nickname, but you can still call me " + name);
        }
    }

}
