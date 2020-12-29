package net.robinfriedli.botify.command.commands.scripting;

import java.util.concurrent.TimeUnit;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.scripting.GroovyVariableManager;
import net.robinfriedli.botify.scripting.SafeGroovyScriptRunner;

public class EvalCommand extends AbstractCommand {

    public EvalCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresValue, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresValue, identifier, description, category);
    }

    @Override
    public void doRun() {
        CommandContext context = getContext();
        Botify botify = Botify.get();
        SecurityManager securityManager = botify.getSecurityManager();
        GroovySandboxComponent groovySandboxComponent = botify.getGroovySandboxComponent();
        GroovyVariableManager groovyVariableManager = botify.getGroovyVariableManager();

        SafeGroovyScriptRunner groovyScriptRunner = new SafeGroovyScriptRunner(
            context,
            groovySandboxComponent,
            groovyVariableManager,
            securityManager,
            argumentSet("privileged")
        );

        groovyScriptRunner.runAndSendResult(getCommandInput(), 10, TimeUnit.SECONDS);
    }

    @Override
    public void onSuccess() {
    }

}
