package net.robinfriedli.aiode.persist.qb.interceptor.interceptors;

import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.entities.CustomPermissionTarget;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.Preset;
import net.robinfriedli.aiode.entities.StoredScript;
import net.robinfriedli.aiode.persist.qb.QueryBuilder;
import net.robinfriedli.aiode.persist.qb.interceptor.QueryInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class PartitionedQueryInterceptor implements QueryInterceptor {

    private static final Set<Class<?>> PARTITIONED_TABLES = ImmutableSet.of(
        Playlist.class,
        Preset.class,
        StoredScript.class,
        CustomPermissionTarget.class
    );

    private final GuildManager guildManager;
    private final String guildId;

    // inject lazy because the GuildManager injects a QueryBuilderFactor, causing a cycle
    @Autowired
    public PartitionedQueryInterceptor(@Lazy GuildManager guildManager) {
        this.guildManager = guildManager;
        guildId = null;
    }

    public PartitionedQueryInterceptor(GuildManager guildManager, String guildId) {
        this.guildManager = guildManager;
        this.guildId = guildId;
    }

    @Override
    public void intercept(QueryBuilder<?, ?, ?, ?> queryBuilder) {
        Optional<ExecutionContext> executionContext = ExecutionContext.Current.optional();
        if (guildManager.getMode() == GuildManager.Mode.PARTITIONED && (executionContext.isPresent() || guildId != null)) {
            String id = executionContext.map(ctx -> ctx.getGuild().getId()).orElse(guildId);
            queryBuilder.where(((cb, root, query) -> cb.equal(root.get("guildId"), id)));
        }
    }

    @Override
    public boolean shouldIntercept(Class<?> entityClass) {
        return PARTITIONED_TABLES.contains(entityClass);
    }

}
