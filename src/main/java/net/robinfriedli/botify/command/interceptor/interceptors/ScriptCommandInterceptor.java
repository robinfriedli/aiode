package net.robinfriedli.botify.command.interceptor.interceptors;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.botify.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.botify.boot.configurations.HibernateComponent;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.StoredScript;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.Abort;
import net.robinfriedli.botify.exceptions.ExceptionUtils;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.scripting.GroovyWhitelistInterceptor;
import net.robinfriedli.botify.scripting.SafeGroovyScriptRunner;
import org.codehaus.groovy.control.CompilerConfiguration;

public class ScriptCommandInterceptor extends AbstractChainableCommandInterceptor {

    private final GroovySandboxComponent groovySandboxComponent;
    private final HibernateComponent hibernateComponent;
    private final MessageService messageService;
    private final QueryBuilderFactory queryBuilderFactory;

    public ScriptCommandInterceptor(CommandInterceptorContribution contribution,
                                    CommandInterceptor next,
                                    GroovySandboxComponent groovySandboxComponent,
                                    HibernateComponent hibernateComponent,
                                    MessageService messageService,
                                    QueryBuilderFactory queryBuilderFactory) {
        super(contribution, next);
        this.hibernateComponent = hibernateComponent;
        this.queryBuilderFactory = queryBuilderFactory;
        this.groovySandboxComponent = groovySandboxComponent;
        this.messageService = messageService;
    }

    @Override
    public void performChained(Command command) {
        if (!(command instanceof AbstractCommand)) {
            return;
        }

        List<StoredScript> scriptInterceptors = hibernateComponent.invokeWithSession(session ->
            queryBuilderFactory.find(StoredScript.class)
                .where((cb, root, subQueryFactory) -> cb.equal(
                    root.get("scriptUsage"),
                    subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk")
                        .where((cb1, root1) -> cb1.equal(root1.get("uniqueId"), "interceptor"))
                        .build(session)
                ))
                .build(session)
                .getResultList());

        CompilerConfiguration compilerConfiguration = groovySandboxComponent.getCompilerConfiguration();
        GroovyWhitelistInterceptor groovyWhitelistInterceptor = groovySandboxComponent.getGroovyWhitelistInterceptor();
        GroovyShell groovyShell = new GroovyShell(compilerConfiguration);
        CommandContext context = command.getContext();
        context.addScriptParameters(groovyShell);
        groovyShell.setVariable("command", command);
        groovyShell.setVariable("messages", messageService);

        SafeGroovyScriptRunner scriptRunner = new SafeGroovyScriptRunner(context, groovyShell, groovyWhitelistInterceptor);
        for (StoredScript scriptInterceptor : scriptInterceptors) {
            try {
                scriptRunner.runWithTimeLimit(scriptInterceptor.getScript(), 5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable error = e.getCause() != null ? e.getCause() : e;

                if (error instanceof Abort) {
                    throw new Abort();
                } else {
                    EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(error);
                    embedBuilder.setTitle("Error occurred while executing custom command interceptor: " + scriptInterceptor.getIdentifier());
                    messageService.sendTemporary(embedBuilder.build(), context.getChannel());
                }
            } catch (TimeoutException e) {
                messageService.sendError(String.format("Execution of script command interceptors stopped because script '%s' has run into a timeout", scriptInterceptor.getIdentifier()), context.getChannel());
            }
        }
    }

}
