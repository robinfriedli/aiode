package net.robinfriedli.botify.command.commands;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;

public class LogoutCommand extends AbstractCommand {

    public LogoutCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier) {
        super(context, commandManager, commandString, false, false, false, identifier,
            "Log out from Spotify.", Category.SPOTIFY);
    }

    @Override
    public void doRun() {
        getManager().getLoginManager().removeLogin(getContext().getUser());
    }

    @Override
    public void onSuccess() {
        sendMessage(getContext().getChannel(), "User " + getContext().getUser().getName() + " logged out from Spotify.");
    }
}
