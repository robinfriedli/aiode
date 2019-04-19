package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.GuildSpecificationManager;

public class RenameCommand extends AbstractCommand {

    private boolean couldChangeNickname;

    public RenameCommand(CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContext, commandManager, commandString, true, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        GuildSpecificationManager guildSpecificationManager = getManager().getGuildManager();
        couldChangeNickname = guildSpecificationManager.setName(getContext().getGuild(), getCommandBody());
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
