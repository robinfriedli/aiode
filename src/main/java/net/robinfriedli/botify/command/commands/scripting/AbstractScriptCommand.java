package net.robinfriedli.botify.command.commands.scripting;

import java.util.List;
import java.util.Optional;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.StoredScript;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.Table2;
import org.codehaus.groovy.control.CompilationFailedException;
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
        QueryBuilderFactory queryBuilderFactory = Botify.get().getQueryBuilderFactory();
        if (argumentSet("delete")) {
            deleteScript(session);
        } else if (getCommandInput().isBlank()) {
            if (argumentSet("identifier")) {
                sendScript(getArgumentValue("identifier"), session);
            } else {
                listAllScripts(queryBuilderFactory, session, context);
            }
        } else if (argumentSet("identifier")) {
            saveNewScript(queryBuilderFactory, session, context);
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
            Table2 table = new Table2(embedBuilder);
            table.addColumn("Identifier", storedScripts, StoredScript::getIdentifier);
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

        GroovyShell groovyShell = new GroovyShell();
        context.addScriptParameters(groovyShell);
        groovyShell.setVariable("messages", getMessageService());
        groovyShell.setVariable("command", this);
        try {
            groovyShell.parse(script);
        } catch (CompilationFailedException e) {
            throw new InvalidCommandException("Could not compile provided script: " + e.getMessage());
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
            embedBuilder.setDescription("```groovy" + System.lineSeparator() + script.getScript() + System.lineSeparator() + "```");
            sendMessage(embedBuilder);
        } else {
            throw new InvalidCommandException(String.format("No such %s '%s'", scriptUsageId, identifier));
        }
    }

    private void deleteScript(Session session) {
        Optional<StoredScript> foundScript = SearchEngine.searchScript(getCommandInput(), scriptUsageId, session);

        if (foundScript.isPresent()) {
            invoke(() -> session.delete(foundScript.get()));
        } else {
            throw new InvalidCommandException(String.format("No saved %s found for '%s'", scriptUsageId, getCommandInput()));
        }
    }

    @Override
    public void onSuccess() {
    }
}
