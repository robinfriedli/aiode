package net.robinfriedli.aiode.command.commands.scripting;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.PermissionTarget;
import net.robinfriedli.aiode.command.argument.ArgumentController;
import net.robinfriedli.aiode.command.argument.ArgumentDefinition;
import net.robinfriedli.aiode.entities.StoredScript;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.scripting.GroovyVariableManager;
import net.robinfriedli.aiode.scripting.SafeGroovyScriptRunner;
import net.robinfriedli.aiode.util.SearchEngine;
import net.robinfriedli.jxp.api.XmlElement;
import org.hibernate.Session;
import org.jetbrains.annotations.Nullable;

public class ScriptCommand extends AbstractScriptCommand {

    private StoredScript script;

    public ScriptCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category, "script");
    }

    @Override
    public void doRun() {
        Aiode aiode = Aiode.get();
        CommandContext context = getContext();
        Session session = context.getSession();
        if (script != null || argumentSet("invoke")) {
            executeScript(aiode, context, session);
        } else {
            super.doRun();
        }
    }

    public void setScript(StoredScript storedScript) {
        script = storedScript;
    }

    private void executeScript(Aiode aiode, CommandContext context, Session session) {
        GroovySandboxComponent groovySandboxComponent = aiode.getGroovySandboxComponent();
        GroovyVariableManager groovyVariableManager = aiode.getGroovyVariableManager();

        SafeGroovyScriptRunner groovyScriptRunner = new SafeGroovyScriptRunner(
            context,
            groovySandboxComponent,
            groovyVariableManager,
            aiode.getSecurityManager(),
            argumentSet("privileged")
        );

        StoredScript script;
        if (this.script != null) {
            script = this.script;
        } else {
            String identifier = getArgumentValue("invoke");
            Optional<StoredScript> storedScript = SearchEngine.searchScript(identifier, "script", session);

            if (storedScript.isPresent()) {
                script = storedScript.get();
            } else {
                throw new InvalidCommandException(String.format("No such script '%s'", identifier));
            }
        }

        if (!script.isActive()) {
            throw new InvalidCommandException(String.format("Script '%s' has been deactivated. Use the active argument to reactivate the script.", script.getIdentifier()));
        }

        groovyScriptRunner.runAndSendResult(script.getScript(), 10, TimeUnit.SECONDS);
    }

    @Override
    protected ArgumentController createArgumentController() {
        return new ScriptArgumentController(this);
    }

    private static class ScriptArgumentController extends ArgumentController {

        private final ScriptCommand scriptCommand;

        public ScriptArgumentController(AbstractCommand sourceCommand) {
            super(sourceCommand);
            scriptCommand = (ScriptCommand) sourceCommand;
        }

        @Override
        public ArgumentDefinition get(String arg) {
            ArgumentDefinition definedArgument = super.get(arg);
            return Objects.requireNonNullElseGet(definedArgument, () -> new DynamicScriptArgument(arg));
        }

        @Override
        public void verify() throws InvalidCommandException {
            super.verify();

            if (!(scriptCommand.script != null || argumentSet("invoke"))) {
                for (ArgumentUsage argumentUsage : getUsedArguments().values()) {
                    ArgumentDefinition argumentDefinition = argumentUsage.getArgument();
                    if (!argumentDefinition.isStatic()) {
                        throw new InvalidCommandException(
                            String.format(
                                "Undefined dynamic arguments may only be used when invoking a script. No such argument '%s' on command '%s'.",
                                argumentDefinition.getIdentifier(),
                                getSourceCommand().getIdentifier()
                            )
                        );
                    }
                }
            }
        }

    }

    private static class DynamicScriptArgument implements ArgumentDefinition {

        private final String identifier;

        private DynamicScriptArgument(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String getDescription() {
            return "Undefined argument used at script command invocation that may be used within the script.";
        }

        @Override
        public List<XmlElement> getExcludedArguments() {
            return Collections.emptyList();
        }

        @Override
        public List<XmlElement> getRequiredArguments() {
            return Collections.emptyList();
        }

        @Override
        public List<XmlElement> getRules() {
            return Collections.emptyList();
        }

        @Override
        public List<XmlElement> getValueChecks() {
            return Collections.emptyList();
        }

        @Override
        public Class<?> getValueType() {
            return String.class;
        }

        @Override
        public boolean requiresValue() {
            return false;
        }

        @Override
        public boolean requiresInput() {
            return false;
        }

        @Override
        public boolean isStatic() {
            return false;
        }

        @Nullable
        @Override
        public PermissionTarget getPermissionTarget() {
            return null;
        }
    }

}
