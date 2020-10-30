package net.robinfriedli.botify.command.interceptor.interceptors;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.StoredScript;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.Abort;
import net.robinfriedli.botify.exceptions.CommandFailure;
import net.robinfriedli.botify.exceptions.ExceptionUtils;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;
import net.robinfriedli.botify.scripting.GroovyVariables;
import net.robinfriedli.botify.scripting.GroovyWhitelistInterceptor;
import net.robinfriedli.botify.scripting.SafeGroovyScriptRunner;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.hibernate.Session;

public abstract class ScriptCommandInterceptor extends AbstractChainableCommandInterceptor {

    private final GroovySandboxComponent groovySandboxComponent;
    private final GuildPropertyManager guildPropertyManager;
    private final MessageService messageService;
    private final QueryBuilderFactory queryBuilderFactory;

    public ScriptCommandInterceptor(CommandInterceptorContribution contribution,
                                    CommandInterceptor next,
                                    GroovySandboxComponent groovySandboxComponent,
                                    GuildPropertyManager guildPropertyManager,
                                    MessageService messageService,
                                    QueryBuilderFactory queryBuilderFactory) {
        super(contribution, next);
        this.guildPropertyManager = guildPropertyManager;
        this.queryBuilderFactory = queryBuilderFactory;
        this.groovySandboxComponent = groovySandboxComponent;
        this.messageService = messageService;
    }

    @Override
    public void performChained(Command command) {
        if (!(command instanceof AbstractCommand && !((AbstractCommand) command).getCommandContribution().isDisableScriptInterceptors())) {
            return;
        }

        CommandContext context = command.getContext();
        Session session = context.getSession();

        GuildSpecification specification = context.getGuildContext().getSpecification(session);
        boolean enableScripting = guildPropertyManager
            .getPropertyValueOptional("enableScripting", Boolean.class, specification)
            .orElse(true);

        if (!enableScripting) {
            return;
        }

        String usageId = getUsageId();
        List<StoredScript> scriptInterceptors = queryBuilderFactory.find(StoredScript.class)
            .where((cb, root, subQueryFactory) -> cb.equal(
                root.get("scriptUsage"),
                subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk")
                    .where((cb1, root1) -> cb1.equal(root1.get("uniqueId"), usageId))
                    .build(session)
            ))
            .build(session)
            .getResultList();

        if (scriptInterceptors.isEmpty()) {
            return;
        }

        CompilerConfiguration compilerConfiguration = groovySandboxComponent.getCompilerConfiguration();
        GroovyWhitelistInterceptor groovyWhitelistInterceptor = groovySandboxComponent.getGroovyWhitelistInterceptor();
        GroovyShell groovyShell = new GroovyShell(compilerConfiguration);
        GroovyVariables.addVariables(groovyShell, context, command, messageService, Botify.get().getSecurityManager());

        SafeGroovyScriptRunner scriptRunner = new SafeGroovyScriptRunner(context, groovyShell, groovyWhitelistInterceptor);
        AtomicReference<StoredScript> currentScriptReference = new AtomicReference<>();
        try {
            scriptRunner.runScripts(scriptInterceptors, currentScriptReference, 5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable error = e.getCause() != null ? e.getCause() : e;

            if (error instanceof Abort) {
                throw new Abort();
            } else if (error instanceof CommandFailure) {
                messageService.sendError(
                    String.format("Executing command %1$ss failed due to an error in %1$s '%2$s'", usageId, currentScriptReference.get().getIdentifier()),
                    context.getChannel()
                );
            } else {
                EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(error);
                StoredScript currentScript = currentScriptReference.get();
                embedBuilder.setTitle(String.format("Error occurred while executing custom command %s%s",
                    usageId,
                    currentScript.getIdentifier() != null ? ": " + currentScript.getIdentifier() : "")
                );
                messageService.sendTemporary(embedBuilder.build(), context.getChannel());
            }
        } catch (TimeoutException e) {
            StoredScript currentScript = currentScriptReference.get();
            messageService.sendError(
                String.format("Execution of script command %ss stopped because script%s has run into a timeout",
                    usageId,
                    currentScript != null ? String.format(" '%s'", currentScript.getIdentifier()) : ""),
                context.getChannel()
            );
        }
    }

    protected abstract String getUsageId();

    public static class ScriptCommandInterceptorPreExecution extends ScriptCommandInterceptor {

        public ScriptCommandInterceptorPreExecution(CommandInterceptorContribution contribution,
                                                    CommandInterceptor next,
                                                    GroovySandboxComponent groovySandboxComponent,
                                                    GuildPropertyManager guildPropertyManager,
                                                    MessageService messageService,
                                                    QueryBuilderFactory queryBuilderFactory) {
            super(contribution, next, groovySandboxComponent, guildPropertyManager, messageService, queryBuilderFactory);
        }

        @Override
        protected String getUsageId() {
            return "interceptor";
        }
    }

    public static class ScriptCommandInterceptorFinalizer extends ScriptCommandInterceptor {

        public ScriptCommandInterceptorFinalizer(CommandInterceptorContribution contribution,
                                                 CommandInterceptor next,
                                                 GroovySandboxComponent groovySandboxComponent,
                                                 GuildPropertyManager guildPropertyManager,
                                                 MessageService messageService,
                                                 QueryBuilderFactory queryBuilderFactory) {
            super(contribution, next, groovySandboxComponent, guildPropertyManager, messageService, queryBuilderFactory);
        }

        @Override
        protected String getUsageId() {
            return "finalizer";
        }
    }

}
