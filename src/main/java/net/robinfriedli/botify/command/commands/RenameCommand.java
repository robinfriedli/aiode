package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.GuildSpecificationManager;

public class RenameCommand extends AbstractCommand {

    public RenameCommand(CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContext, commandManager, commandString, false, false, true, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        GuildSpecificationManager guildSpecificationManager = getManager().getGuildManager();
        guildSpecificationManager.setName(getContext().getGuild(), getCommandBody());
    }

    @Override
    public void onSuccess() {
        String name = getManager().getGuildManager().getNameForGuild(getContext().getGuild());
        sendMessage(getContext().getChannel(), "You can now call me " + name);
    }

}
