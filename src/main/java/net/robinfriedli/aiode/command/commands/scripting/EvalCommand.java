package net.robinfriedli.aiode.command.commands.scripting;

import java.util.concurrent.TimeUnit;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.SecurityManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.scripting.GroovyVariableManager;
import net.robinfriedli.aiode.scripting.SafeGroovyScriptRunner;

public class EvalCommand extends AbstractCommand {

    public EvalCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresValue, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresValue, identifier, description, category);
    }

    @Override
    public void doRun() {
        CommandContext context = getContext();
        Aiode aiode = Aiode.get();
        SecurityManager securityManager = aiode.getSecurityManager();
        GroovySandboxComponent groovySandboxComponent = aiode.getGroovySandboxComponent();
        GroovyVariableManager groovyVariableManager = aiode.getGroovyVariableManager();

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
