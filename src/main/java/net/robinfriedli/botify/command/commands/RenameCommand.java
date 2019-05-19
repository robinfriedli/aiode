package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class RenameCommand extends AbstractCommand {

    private boolean couldChangeNickname;

    public RenameCommand(CommandContribution commandContribution, CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, commandContext, commandManager, commandString, true, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        GuildManager guildManager = getManager().getGuildManager();

        if (getCommandBody().length() < 1 || getCommandBody().length() > 10) {
            throw new InvalidCommandException("Length should be 1 - 10 characters");
        }

        couldChangeNickname = guildManager.setName(getContext().getGuild(), getCommandBody());
    }

    @Override
    public void onSuccess() {
        String name = getManager().getGuildManager().getNameForGuild(getContext().getGuild());
        if (couldChangeNickname) {
            sendSuccess(getContext().getChannel(), "You can now call me " + name);
        } else {
            sendSuccess(getContext().getChannel(), "I do not have permission to change my nickname, but you can still call me " + getCommandBody());
        }
    }

}
