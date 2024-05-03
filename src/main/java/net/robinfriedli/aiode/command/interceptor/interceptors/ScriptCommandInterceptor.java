package net.robinfriedli.aiode.command.interceptor.interceptors;

import java.util.List;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.aiode.command.interceptor.CommandInterceptor;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.StoredScript;
import net.robinfriedli.aiode.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import net.robinfriedli.aiode.scripting.SafeGroovyScriptRunner;
import org.hibernate.Session;

public abstract class ScriptCommandInterceptor extends AbstractChainableCommandInterceptor {

    private final GroovySandboxComponent groovySandboxComponent;
    private final GuildPropertyManager guildPropertyManager;
    private final QueryBuilderFactory queryBuilderFactory;

    public ScriptCommandInterceptor(CommandInterceptorContribution contribution,
                                    CommandInterceptor next,
                                    GroovySandboxComponent groovySandboxComponent,
                                    GuildPropertyManager guildPropertyManager,
                                    QueryBuilderFactory queryBuilderFactory) {
        super(contribution, next);
        this.guildPropertyManager = guildPropertyManager;
        this.queryBuilderFactory = queryBuilderFactory;
        this.groovySandboxComponent = groovySandboxComponent;
    }

    @Override
    public void performChained(Command command) {
        if (command instanceof AbstractCommand abstractCommand) {
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
                    root.get("scriptUsage").get("pk"),
                    subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk", Long.class)
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

        scriptRunner.runScripts(scriptInterceptors, usageId);
    }

    protected abstract String getUsageId();

    public static class ScriptCommandInterceptorPreExecution extends ScriptCommandInterceptor {

        public ScriptCommandInterceptorPreExecution(CommandInterceptorContribution contribution,
                                                    CommandInterceptor next,
                                                    GroovySandboxComponent groovySandboxComponent,
                                                    GuildPropertyManager guildPropertyManager,
                                                    QueryBuilderFactory queryBuilderFactory) {
            super(contribution, next, groovySandboxComponent, guildPropertyManager, queryBuilderFactory);
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
                                                 QueryBuilderFactory queryBuilderFactory) {
            super(contribution, next, groovySandboxComponent, guildPropertyManager, queryBuilderFactory);
        }

        @Override
        protected String getUsageId() {
            return "finalizer";
        }
    }

}
