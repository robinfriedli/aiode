package net.robinfriedli.aiode.command.commands.spotify;

import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.login.Login;
import net.robinfriedli.aiode.login.LoginManager;

public class LogoutCommand extends AbstractCommand {

    public LogoutCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        LoginManager loginManager = Aiode.get().getLoginManager();
        User user = getContext().getUser();
        Login login = loginManager.requireLoginForUser(user);
        login.cancel();
        loginManager.removeLogin(user);
    }

    @Override
    public void onSuccess() {
        sendSuccess("User " + getContext().getUser().getName() + " logged out from Spotify.");
    }
}
