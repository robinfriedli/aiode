package net.robinfriedli.aiode.boot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.annotation.Nullable;

import net.robinfriedli.jxp.api.StringConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;

@Configuration
@PropertySource("classpath:application.properties")
@PropertySource("classpath:settings-private.properties")
@ComponentScan("net.robinfriedli.aiode")
public class SpringPropertiesConfig {

    private final Properties applicationProperties;
    private final Properties privateProperties;

    public SpringPropertiesConfig(@Value("classpath:application.properties") Resource applicationPropertiesResource,
                                  @Value("classpath:settings-private.properties") Resource privatePropertiesResource) {
        try {
            InputStream applicationPropertiesResourceInputStream = applicationPropertiesResource.getInputStream();
            InputStream privatePropertiesResourceInputStream = privatePropertiesResource.getInputStream();

            Properties applicationProperties = new Properties();
            applicationProperties.load(applicationPropertiesResourceInputStream);

            Properties privateProperties = new Properties();
            privateProperties.load(privatePropertiesResourceInputStream);

            applicationPropertiesResourceInputStream.close();
            privatePropertiesResourceInputStream.close();

            this.applicationProperties = applicationProperties;
            this.privateProperties = privateProperties;
        } catch (IOException e) {
            throw new RuntimeException("Exception while reading properties", e);
        }
    }

    public <E> E requireApplicationProperty(Class<E> type, String name) {
        return StringConverter.convert(requireApplicationProperty(name), type);
    }

    @Nullable
    public <E> E getApplicationProperty(Class<E> type, String name) {
        String applicationProperty = getApplicationProperty(name);

        if (applicationProperty != null) {
            return StringConverter.convert(applicationProperty, type);
        }

        return null;
    }

    public <E> E requirePrivateProperty(Class<E> type, String name) {
        return StringConverter.convert(requirePrivateProperty(name), type);
    }

    @Nullable
    public <E> E getPrivateProperty(Class<E> type, String name) {
        String privateProperty = getPrivateProperty(name);

        if (privateProperty != null) {
            return StringConverter.convert(privateProperty, type);
        }

        return null;
    }

    public String requireApplicationProperty(String name) {
        String applicationProperty = getApplicationProperty(name);

        if (applicationProperty == null) {
            throw new IllegalStateException("Property '" + name + "' not set");
        }

        return applicationProperty;
    }

    @Nullable
    public String getApplicationProperty(String name) {
        return applicationProperties.getProperty(name);
    }

    public String requirePrivateProperty(String name) {
        String privateProperty = getPrivateProperty(name);

        if (privateProperty == null) {
            throw new IllegalStateException("Property '" + name + "' not set");
        }

        return privateProperty;
    }

    @Nullable
    public String getPrivateProperty(String name) {
        return privateProperties.getProperty(name);
    }

}
