package net.robinfriedli.aiode.command.commands.scripting;

import java.util.List;
import java.util.Optional;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.StoredScript;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.ExceptionUtils;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import net.robinfriedli.aiode.scripting.GroovyVariableManager;
import net.robinfriedli.aiode.util.EmbedTable;
import net.robinfriedli.aiode.util.SearchEngine;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.hibernate.Session;

public abstract class AbstractScriptCommand extends AbstractCommand {

    private final String scriptUsageId;

    public AbstractScriptCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category, String scriptUsageId) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category);
        this.scriptUsageId = scriptUsageId;
    }

    @Override
    public void doRun() {
        CommandContext context = getContext();
        Session session = context.getSession();
        QueryBuilderFactory queryBuilderFactory = Aiode.get().getQueryBuilderFactory();
        if (argumentSet("delete")) {
            StoredScript foundScript = findScript(session);
            invoke(() -> session.remove(foundScript));
        } else if (argumentSet("identifier") && !getCommandInput().isBlank()) {
            saveNewScript(queryBuilderFactory, session, context);
        } else if (argumentSet("activate")) {
            toggleActivation(false, session);
        } else if (argumentSet("deactivate")) {
            toggleActivation(true, session);
        } else if (getCommandInput().isBlank()) {
            if (argumentSet("identifier")) {
                sendScript(getArgumentValue("identifier"), session);
            } else {
                listAllScripts(queryBuilderFactory, session, context);
            }
        } else {
            sendScript(getCommandInput(), session);
        }
    }

    private void listAllScripts(QueryBuilderFactory queryBuilderFactory, Session session, CommandContext context) {
        List<StoredScript> storedScripts = queryBuilderFactory
            .find(StoredScript.class)
            .where((cb, root, subQueryFactory) -> cb.equal(
                root.get("scriptUsage"),
                subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk")
                    .where((cb1, root1) -> cb1.equal(root1.get("uniqueId"), scriptUsageId))
                    .build(session)
            ))
            .build(session)
            .getResultList();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        if (storedScripts.isEmpty()) {
            embedBuilder.setDescription(String.format("No %ss saved", scriptUsageId));
            getMessageService().sendTemporary(embedBuilder, context.getChannel());
        } else {
            embedBuilder.setDescription(String.format("Show a specific %s by entering its identifier", scriptUsageId));
            EmbedTable table = new EmbedTable(embedBuilder);
            table.addColumn("Identifier", storedScripts, StoredScript::getIdentifier);
            table.addColumn("Active", storedScripts, script -> String.valueOf(script.isActive()));
            table.build();
            sendMessage(embedBuilder);
        }
    }

    private void saveNewScript(QueryBuilderFactory queryBuilderFactory, Session session, CommandContext context) {
        String identifier = getArgumentValue("identifier");
        String script = getCommandInput();

        Optional<StoredScript> existingScript = SearchEngine.searchScript(identifier, scriptUsageId, session);

        if (existingScript.isPresent()) {
            throw new InvalidCommandException(String.format("%s with identifier '%s' already exists", scriptUsageId, identifier));
        }

        Aiode aiode = Aiode.get();
        GroovyVariableManager groovyVariableManager = aiode.getGroovyVariableManager();

        GroovyShell groovyShell;
        if (argumentSet("privileged")) {
            groovyShell = new GroovyShell();
        } else {
            groovyShell = new GroovyShell(aiode.getGroovySandboxComponent().getCompilerConfiguration());
        }

        groovyVariableManager.prepareShell(groovyShell);
        try {
            groovyShell.parse(script);
        } catch (MultipleCompilationErrorsException e) {
            throw new InvalidCommandException(
                String.format("Could not compile provided script:%s%s", System.lineSeparator(), ExceptionUtils.formatScriptCompilationError(e))
            );
        }

        StoredScript.ScriptUsage scriptUsage = queryBuilderFactory
            .find(StoredScript.ScriptUsage.class)
            .where((cb, root) -> cb.equal(root.get("uniqueId"), scriptUsageId))
            .build(session)
            .uniqueResult();

        StoredScript storedScript = new StoredScript();
        storedScript.setGuildId(context.getGuild().getIdLong());
        storedScript.setIdentifier(identifier);
        storedScript.setScript(script);
        storedScript.setScriptUsage(scriptUsage);
        storedScript.setActive(!argumentSet("deactivate"));

        invoke(() -> session.persist(storedScript));
    }

    private void sendScript(String identifier, Session session) {
        Optional<StoredScript> storedScript = SearchEngine.searchScript(identifier, scriptUsageId, session);

        if (storedScript.isPresent()) {
            StoredScript script = storedScript.get();
            String firstLetter = scriptUsageId.length() > 0 ? scriptUsageId.substring(0, 1).toUpperCase() : "";
            String rest = scriptUsageId.length() > 1 ? scriptUsageId.substring(1) : "";
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle(String.format("%s%s: %s", firstLetter, rest, script.getIdentifier()));
            embedBuilder.addField("Active", String.valueOf(script.isActive()), true);
            embedBuilder.addField("Groovy script", "```groovy" + System.lineSeparator() + script.getScript() + System.lineSeparator() + "```", false);
            sendMessage(embedBuilder);
        } else {
            throw new InvalidCommandException(String.format("No such %s '%s'", scriptUsageId, identifier));
        }
    }

    private StoredScript findScript(Session session) {
        String identifier;
        if (argumentSet("identifier")) {
            identifier = getArgumentValue("identifier");
        } else if (!getCommandInput().isBlank()) {
            identifier = getCommandInput();
        } else {
            throw new InvalidCommandException("Expected either the command input or identifier argument to specify the target script.");
        }

        Optional<StoredScript> foundScript = SearchEngine.searchScript(identifier, scriptUsageId, session);
        if (foundScript.isPresent()) {
            return foundScript.get();
        } else {
            throw new InvalidCommandException(String.format("No saved %s found for '%s'", scriptUsageId, identifier));
        }
    }

    private void toggleActivation(boolean deactivate, Session session) {
        StoredScript script = findScript(session);

        if (deactivate) {
            if (script.isActive()) {
                invoke(() -> script.setActive(false));
            } else {
                throw new InvalidCommandException("Script is already inactive.");
            }
        } else {
            if (script.isActive()) {
                throw new InvalidCommandException("Script is already active.");
            } else {
                invoke(() -> script.setActive(true));
            }
        }
    }

    @Override
    public void onSuccess() {
    }
}
