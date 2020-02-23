package net.robinfriedli.botify.persist.qb.interceptor.interceptors;

import java.util.Optional;

import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.persist.qb.QueryBuilder;
import net.robinfriedli.botify.persist.qb.interceptor.QueryInterceptor;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Query interceptor that filters access configurations that are related to the guild specification entity of the current
 * guild
 */
@Component
public class AccessConfigurationPartitionInterceptor implements QueryInterceptor {

    private Session session;
    private String guildId;

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
                root.get("guildSpecification"),
                subQueryFactory.createUncorrelatedSubQuery(GuildSpecification.class, "pk").where((cb1, root1, subQueryFactory1) ->
                    cb1.equal(root1.get("guildId"), id)).build(session)
            ));
        }
    }

    @Override
    public boolean shouldIntercept(Class<?> entityClass) {
        return entityClass == AccessConfiguration.class;
    }

}
