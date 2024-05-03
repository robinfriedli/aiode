package net.robinfriedli.aiode.persist.qb.interceptor.interceptors;

import java.util.Optional;

import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.entities.AccessConfiguration;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.persist.qb.QueryBuilder;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptor;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Query interceptor that filters access configurations that are related to the guild specification entity of the current
 * guild
 */
@Component
public class AccessConfigurationPartitionInterceptor implements QueryInterceptor {

    private final Session session;
    private final String guildId;

    @Autowired
    public AccessConfigurationPartitionInterceptor() {
        session = null;
        guildId = null;
    }

    public AccessConfigurationPartitionInterceptor(Session session, String guildId) {
        this.session = session;
        this.guildId = guildId;
    }

    @Override
    public void intercept(QueryBuilder<?, ?, ?, ?> queryBuilder) {
        Optional<ExecutionContext> executionContext = ExecutionContext.Current.optional();
        if (executionContext.isPresent() || (session != null && guildId != null)) {
            Session session = executionContext.map(ExecutionContext::getSession).orElse(this.session);
            String id = executionContext.map(ctx -> ctx.getGuild().getId()).orElse(guildId);
            queryBuilder.where((cb, root, subQueryFactory) -> cb.equal(
                root.get("guildSpecification").get("pk"),
                subQueryFactory.createUncorrelatedSubQuery(GuildSpecification.class, "pk", Long.class).where((cb1, root1, subQueryFactory1) ->
                    cb1.equal(root1.get("guildId"), id)).build(session)
            ));
        }
    }

    @Override
    public boolean shouldIntercept(Class<?> entityClass) {
        return entityClass == AccessConfiguration.class;
    }

}
