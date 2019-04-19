package net.robinfriedli.botify.command.commands;

import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.login.LoginManager;

public class LogoutCommand extends AbstractCommand {

    public LogoutCommand(CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(context, commandManager, commandString, false, identifier, description, Category.SPOTIFY);
    }

    @Override
    public void doRun() {
        LoginManager loginManager = getManager().getLoginManager();
        User user = getContext().getUser();
        loginManager.requireLoginForUser(user);
        loginManager.removeLogin(user);
    }

    @Override
    public void onSuccess() {
        sendSuccess(getContext().getChannel(), "User " + getContext().getUser().getName() + " logged out from Spotify.");
    }
}
