package net.robinfriedli.botify.discord.properties;

import java.util.List;
import java.util.stream.Collectors;

import net.robinfriedli.botify.entities.xml.GenericClassContribution;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class GuildPropertyManager {

    private final Context propertyContext;

    public GuildPropertyManager(Context propertyContext) {
        this.propertyContext = propertyContext;
    }

    public List<AbstractGuildProperty> getProperties() {
        return propertyContext.getInstancesOf(GuildPropertyContribution.class).stream()
            .map(GenericClassContribution::instantiate)
            .collect(Collectors.toList());
    }

    public AbstractGuildProperty getProperty(String property) {
        GuildPropertyContribution contribution = propertyContext.query(attribute("property").is(property), GuildPropertyContribution.class).getOnlyResult();

        if (contribution != null) {
            return contribution.instantiate();
        }

        return null;
    }

    public AbstractGuildProperty getPropertyByName(String name) {
        GuildPropertyContribution contribution = propertyContext
            .query(SearchEngine.editDistanceAttributeCondition("name", name), GuildPropertyContribution.class)
            .getOnlyResult();

        if (contribution != null) {
            return contribution.instantiate();
        }

        return null;
    }

}
