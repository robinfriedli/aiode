package net.robinfriedli.botify.command.commands.admin;

import java.net.URL;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;

public class ReloadContextCommand extends AbstractAdminCommand {

    public ReloadContextCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description);
    }

    @Override
    public void runAdmin() {
        JxpBackend jxpBackend = Botify.get().getJxpBackend();

        URL resource = getClass().getResource(getCommandInput());
        if (resource == null) {
            throw new InvalidCommandException("No resource found for '" + getCommandInput() + "' in classpath");
        }
        String file = resource.getFile();

        Context context = jxpBackend.getExistingContext(file);

        if (context == null) {
            throw new InvalidCommandException("No context mapped to " + getCommandInput());
        }

        context.reload();
    }

    @Override
    public void onSuccess() {
        sendSuccess("Context reloaded");
    }
}
