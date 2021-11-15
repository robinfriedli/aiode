package net.robinfriedli.aiode.command.commands.scripting;

import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;

public class InterceptorCommand extends AbstractScriptCommand {

    public InterceptorCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category, "interceptor");
    }

}
