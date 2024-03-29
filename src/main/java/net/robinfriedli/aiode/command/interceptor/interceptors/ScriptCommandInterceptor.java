package net.robinfriedli.aiode.command.interceptor.interceptors;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.aiode.command.interceptor.CommandInterceptor;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.StoredScript;
import net.robinfriedli.aiode.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.aiode.exceptions.CommandFailure;
import net.robinfriedli.aiode.exceptions.ExceptionUtils;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import net.robinfriedli.aiode.scripting.SafeGroovyScriptRunner;
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
        if (command instanceof AbstractCommand) {
            AbstractCommand abstractCommand = (AbstractCommand) command;
            if (abstractCommand.getArgumentController().argumentSet("skipInterceptors")
                || abstractCommand.getCommandContribution().isDisableScriptInterceptors()) {
                return;
            }
        } else {
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
            .where((cb, root, subQueryFactory) -> cb.and(
                cb.isTrue(root.get("active")),
                cb.equal(
                    root.get("scriptUsage"),
                    subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk")
                        .where((cb1, root1) -> cb1.equal(root1.get("uniqueId"), usageId))
                        .build(session)
                )
            ))
            .build(session)
            .getResultList();

        if (scriptInterceptors.isEmpty()) {
            return;
        }

        Aiode aiode = Aiode.get();
        SafeGroovyScriptRunner scriptRunner = new SafeGroovyScriptRunner(
            context,
            groovySandboxComponent,
            aiode.getGroovyVariableManager(),
            aiode.getSecurityManager(),
            false
        );

        AtomicReference<StoredScript> currentScriptReference = new AtomicReference<>();
        try {
            scriptRunner.runScripts(scriptInterceptors, currentScriptReference, 5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable error = e.getCause() != null ? e.getCause() : e;

            if (error instanceof CommandFailure) {
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
