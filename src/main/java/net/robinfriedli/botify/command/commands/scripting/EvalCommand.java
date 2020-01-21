package net.robinfriedli.botify.command.commands.scripting;

import java.util.concurrent.TimeUnit;

import groovy.lang.GroovyShell;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.scripting.GroovyWhitelistInterceptor;
import net.robinfriedli.botify.scripting.SafeGroovyScriptRunner;
import org.codehaus.groovy.control.CompilerConfiguration;

public class EvalCommand extends AbstractCommand {

    public EvalCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresValue, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresValue, identifier, description, category);
    }

    @Override
    public void doRun() {
        CommandContext context = getContext();
        GroovySandboxComponent groovySandboxComponent = Botify.get().getGroovySandboxComponent();
        CompilerConfiguration compilerConfiguration = groovySandboxComponent.getCompilerConfiguration();
        GroovyWhitelistInterceptor groovyWhitelistInterceptor = groovySandboxComponent.getGroovyWhitelistInterceptor();
        GroovyShell groovyShell = new GroovyShell(compilerConfiguration);
        context.addScriptParameters(groovyShell);
        groovyShell.setVariable("messages", getMessageService());
        groovyShell.setVariable("command", this);
        SafeGroovyScriptRunner groovyScriptRunner = new SafeGroovyScriptRunner(context, groovyShell, groovyWhitelistInterceptor);

        groovyScriptRunner.runAndSendResult(getCommandInput(), 1, TimeUnit.MINUTES);
    }

    @Override
    public void onSuccess() {
    }

}
