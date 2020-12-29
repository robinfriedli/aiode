package net.robinfriedli.botify.entities;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.commands.scripting.ScriptCommand;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.persist.qb.QueryBuilderFactory;

@Entity
@Table(name = "stored_script")
public class StoredScript implements Serializable, SanitizedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(length = 30)
    @Size(min = 1, max = 30, message = "Maximum length for interceptor name is 30")
    private String identifier;
    @Column(length = 1000)
    @Size(max = 1000, message = "Maximum length for interceptor script is 1000")
    private String script;
    @Column(name = "guild_id")
    private long guildId;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "script_usage_pk")
    private ScriptUsage scriptUsage;

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    @Override
    public int getMaxEntityCount(SpringPropertiesConfig springPropertiesConfig) {
        String usageUniqueId = scriptUsage.getUniqueId();
        if (usageUniqueId.equals("interceptor") || usageUniqueId.equals("finalizer")) {
            Integer interceptorCountMax = springPropertiesConfig.getApplicationProperty(Integer.class, "botify.preferences.interceptor_count_max");
            return Objects.requireNonNullElse(interceptorCountMax, 0);
        } else {
            Integer scriptCountMax = springPropertiesConfig.getApplicationProperty(Integer.class, "botify.preferences.script_count_max");
            return Objects.requireNonNullElse(scriptCountMax, 0);
        }
    }

    @Override
    public String getIdentifierPropertyName() {
        return "identifier";
    }

    @Override
    public void addCountUnit(List<CountUnit> countUnits, QueryBuilderFactory queryBuilderFactory, SpringPropertiesConfig springPropertiesConfig) {
        String scriptUsageId = getScriptUsage().getUniqueId();

        if (scriptUsageId.equals("interceptor") || scriptUsageId.equals("finalizer")) {
            boolean hasNoCountUnitForInterceptorUsage = countUnits.stream()
                .map(CountUnit::getEntity)
                .filter(e -> e instanceof StoredScript)
                .map(e -> (StoredScript) e)
                .noneMatch(s -> {
                    String usageId = s.getScriptUsage().getUniqueId();
                    return usageId.equals("interceptor") || usageId.equals("finalizer");
                });

            if (hasNoCountUnitForInterceptorUsage) {
                int maxEntityCount = getMaxEntityCount(springPropertiesConfig);

                if (maxEntityCount > 0) {
                    CountUnit countUnit = new CountUnit(
                        getClass(),
                        this,
                        session -> queryBuilderFactory.select(StoredScript.class, (from, cb) -> cb.count(from.get("pk")), Long.class)
                            .where((cb, root, subQueryFactory) -> cb.or(
                                cb.equal(
                                    root.get("scriptUsage"),
                                    subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk")
                                        .where((cb1, root1) -> cb1.equal(root1.get("uniqueId"), "interceptor"))
                                        .build(session)
                                ),
                                cb.equal(
                                    root.get("scriptUsage"),
                                    subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk")
                                        .where((cb1, root1) -> cb1.equal(root1.get("uniqueId"), "finalizer"))
                                        .build(session)
                                )
                            )),
                        String.format("Maximum interceptor / finalizer count of %d reached", maxEntityCount),
                        maxEntityCount
                    );

                    countUnits.add(countUnit);
                }
            }
        } else {
            addDefaultCountUnit(countUnits, scriptUsageId, queryBuilderFactory, springPropertiesConfig);
        }
    }

    private void addDefaultCountUnit(List<CountUnit> countUnits, String scriptUsageId, QueryBuilderFactory queryBuilderFactory, SpringPropertiesConfig springPropertiesConfig) {
        boolean hasNoCountUnitForScriptUsageType = countUnits.stream()
            .map(CountUnit::getEntity)
            .filter(e -> e instanceof StoredScript)
            .map(e -> (StoredScript) e)
            .noneMatch(s -> s.getScriptUsage().getUniqueId().equals(scriptUsageId));

        if (hasNoCountUnitForScriptUsageType) {
            int maxEntityCount = getMaxEntityCount(springPropertiesConfig);

            if (maxEntityCount > 0) {
                CountUnit countUnit = new CountUnit(
                    getClass(),
                    this,
                    session -> queryBuilderFactory.select(StoredScript.class, (from, cb) -> cb.count(from.get("pk")), Long.class)
                        .where((cb, root, subQueryFactory) -> cb.equal(
                            root.get("scriptUsage"),
                            subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk")
                                .where((cb1, root1) -> cb1.equal(root1.get("uniqueId"), scriptUsageId))
                                .build(session)
                        )),
                    String.format("Maximum %s count of %s reached", scriptUsageId, maxEntityCount),
                    maxEntityCount
                );

                countUnits.add(countUnit);
            }
        }
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public void setSanitizedIdentifier(String sanitizedIdentifier) {
        setIdentifier(sanitizedIdentifier);
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public long getGuildId() {
        return guildId;
    }

    public void setGuildId(long guildId) {
        this.guildId = guildId;
    }

    public ScriptUsage getScriptUsage() {
        return scriptUsage;
    }

    public void setScriptUsage(ScriptUsage scriptUsage) {
        this.scriptUsage = scriptUsage;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public ScriptCommand asCommand(CommandManager commandManager, CommandContext context, String input) {
        CommandContribution scriptCommandContribution = commandManager.getCommandContribution("script");

        String commandBody = input.substring(identifier.length()).trim();

        ScriptCommand scriptCommand = (ScriptCommand) scriptCommandContribution.instantiate(commandManager, context, commandBody);
        scriptCommand.setScript(this);
        return scriptCommand;
    }

    @Entity
    @Table(name = "script_usage")
    public static class ScriptUsage implements Serializable {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "pk")
        private long pk;
        @Column(name = "unique_id", unique = true)
        private String uniqueId;

        public long getPk() {
            return pk;
        }

        public void setPk(long pk) {
            this.pk = pk;
        }

        public String getUniqueId() {
            return uniqueId;
        }

        public void setUniqueId(String uniqueId) {
            this.uniqueId = uniqueId;
        }

    }

}
