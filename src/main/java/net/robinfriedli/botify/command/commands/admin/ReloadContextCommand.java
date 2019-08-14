package net.robinfriedli.botify.command.commands.admin;

import com.google.common.base.Strings;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;

public class ReloadContextCommand extends AbstractAdminCommand {

    public ReloadContextCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, true, identifier, description);
    }

    @Override
    public void runAdmin() {
        JxpBackend jxpBackend = Botify.get().getJxpBackend();
        Context context;
        if (argumentSet("property")) {
            String property = PropertiesLoadingService.loadProperty(getCommandInput());
            if (Strings.isNullOrEmpty(property)) {
                throw new InvalidCommandException("Property " + getCommandInput() + " not set");
            }
            context = jxpBackend.getExistingContext(property);
        } else {
            context = jxpBackend.getExistingContext(getCommandInput());
        }

        if (context == null) {
            throw new InvalidCommandException("No context mapped to " + getCommandInput());
        }

        context.reload();
    }

    @Override
    public void onSuccess() {
        sendSuccess("Context reloaded");
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("property")
            .setDescription("Use a property from the settings.properties file that points to a context path.");
        return argumentContribution;
    }
}
