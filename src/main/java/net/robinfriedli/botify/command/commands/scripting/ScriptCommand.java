package net.robinfriedli.botify.command.commands.scripting;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import groovy.lang.GroovyShell;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.StoredScript;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.scripting.GroovyVariables;
import net.robinfriedli.botify.scripting.GroovyWhitelistInterceptor;
import net.robinfriedli.botify.scripting.SafeGroovyScriptRunner;
import net.robinfriedli.botify.util.SearchEngine;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.hibernate.Session;

public class ScriptCommand extends AbstractScriptCommand {

    private StoredScript script;

    public ScriptCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category, "script");
    }

    @Override
    public void doRun() {
        Botify botify = Botify.get();
        CommandContext context = getContext();
        Session session = context.getSession();
        if (script != null || argumentSet("invoke")) {
            executeScript(botify, context, session);
        } else {
            super.doRun();
        }
    }

    public void setScript(StoredScript storedScript) {
        script = storedScript;
    }

    private void executeScript(Botify botify, CommandContext context, Session session) {
        GroovySandboxComponent groovySandboxComponent = botify.getGroovySandboxComponent();
        GroovyWhitelistInterceptor groovyWhitelistInterceptor = groovySandboxComponent.getGroovyWhitelistInterceptor();
        CompilerConfiguration compilerConfiguration = groovySandboxComponent.getCompilerConfiguration();
        GroovyShell groovyShell = new GroovyShell(compilerConfiguration);
        GroovyVariables.addVariables(groovyShell, context, this, getMessageService(), botify.getSecurityManager());
        groovyShell.setVariable("input", getCommandInput());
        SafeGroovyScriptRunner groovyScriptRunner = new SafeGroovyScriptRunner(context, groovyShell, groovyWhitelistInterceptor);

        String scriptString;
        if (script != null) {
            scriptString = script.getScript();
        } else {
            String identifier = getArgumentValue("invoke");
            Optional<StoredScript> storedScript = SearchEngine.searchScript(identifier, "script", session);

            if (storedScript.isPresent()) {
                scriptString = storedScript.get().getScript();
            } else {
                throw new InvalidCommandException(String.format("No such script '%s'", identifier));
            }
        }

        groovyScriptRunner.runAndSendResult(scriptString, 1, TimeUnit.MINUTES);
    }

}
