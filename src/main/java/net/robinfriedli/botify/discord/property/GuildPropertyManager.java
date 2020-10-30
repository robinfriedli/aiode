package net.robinfriedli.botify.discord.property;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GenericClassContribution;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Manager of guild properties defined in guildProperties.xml
 */
@Component
public class GuildPropertyManager {

    private final Context propertyContext;

    public GuildPropertyManager(@Value("classpath:xml-contributions/guildProperties.xml") Resource commandResource, JxpBackend jxpBackend) {
        try {
            this.propertyContext = jxpBackend.createContext(commandResource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Could not instantiate " + getClass().getSimpleName(), e);
        }
    }

    public List<AbstractGuildProperty> getProperties() {
        return propertyContext.getInstancesOf(GuildPropertyContribution.class).stream()
            .map(GenericClassContribution::instantiate)
            .collect(Collectors.toList());
    }

    @Nullable
    public AbstractGuildProperty getProperty(String property) {
        GuildPropertyContribution contribution = propertyContext.query(attribute("property").is(property), GuildPropertyContribution.class).getOnlyResult();

        if (contribution != null) {
            return contribution.instantiate();
        }

        return null;
    }

    public Optional<AbstractGuildProperty> getPropertyOptional(String property) {
        return Optional.ofNullable(getProperty(property));
    }

    public AbstractGuildProperty requireProperty(String property) {
        return Optional.ofNullable(getProperty(property)).orElseThrow();
    }

    @Nullable
    public AbstractGuildProperty getPropertyByName(String name) {
        GuildPropertyContribution contribution = propertyContext
            .query(SearchEngine.editDistanceAttributeCondition("name", name), GuildPropertyContribution.class)
            .getOnlyResult();

        if (contribution != null) {
            return contribution.instantiate();
        }

        return null;
    }

    public AbstractGuildProperty requirePropertyByName(String name) {
        return Optional.ofNullable(getPropertyByName(name)).orElseThrow();
    }

    public <T> Optional<T> getPropertyValueOptional(String property, Class<T> type, GuildSpecification specification) {
        return getPropertyOptional(property).map(p -> p.get(type, specification));
    }

    @Nullable
    public <T> T getPropertyValue(String property, Class<T> type, GuildSpecification specification) {
        return getPropertyValueOptional(property, type, specification).orElse(null);
    }

    public <T> T requirePropertyValue(String property, Class<T> type, GuildSpecification specification) {
        return getPropertyValueOptional(property, type, specification).orElseThrow();
    }

    public <T> Optional<T> getPropertyValueOptional(String property, Class<T> type) {
        return getPropertyOptional(property).map(p -> p.get(type));
    }

    @Nullable
    public <T> T getPropertyValue(String property, Class<T> type) {
        return getPropertyValueOptional(property, type).orElse(null);
    }

    public <T> T requirePropertyValue(String property, Class<T> type) {
        return getPropertyValueOptional(property, type).orElseThrow();
    }

    public Optional<Object> getPropertyValueOptional(String property, GuildSpecification specification) {
        return getPropertyOptional(property).map(p -> p.get(specification));
    }

    @Nullable
    public Object getPropertyValue(String property, GuildSpecification specification) {
        return getPropertyValueOptional(property, specification).orElse(null);
    }

    public Object requirePropertyValue(String property, GuildSpecification specification) {
        return getPropertyValueOptional(property, specification).orElseThrow();
    }

    public Optional<Object> getPropertyValueOptional(String property) {
        return getPropertyOptional(property).map(AbstractGuildProperty::get);
    }

    @Nullable
    public Object getPropertyValue(String property) {
        return getPropertyValueOptional(property).orElse(null);
    }

    public Object requirePropertyValue(String property) {
        return getPropertyValueOptional(property).orElseThrow();
    }

}
