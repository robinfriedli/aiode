package net.robinfriedli.botify.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import javax.annotation.Nullable;

import com.google.common.base.Strings;
import net.robinfriedli.jxp.api.StringConverter;

public class PropertiesLoadingService {

    public static File requireContributionFile(String resourceName) {
        return requireResourceFile("xml-contributions/" + resourceName);
    }

    public static File requireResourceFile(String resourceName) {
        File resourceFile = getResourceFile("/" + resourceName);

        if (resourceFile == null) {
            throw new IllegalArgumentException("No such resource " + resourceName);
        }

        return resourceFile;
    }

    @Nullable
    public static File getResourceFile(String resourceName) {
        URL resource = PropertiesLoadingService.class.getResource(resourceName);
        if (resource != null) {
            return new File(resource.getFile());
        }

        return null;
    }

    public static String requireProperty(String key) {
        String property = loadProperty(key);
        if (!Strings.isNullOrEmpty(property)) {
            return property;
        } else {
            throw new IllegalStateException("Property " + key + " not set");
        }
    }

    public static <E> E requireProperty(Class<E> type, String key) {
        String property = requireProperty(key);
        return StringConverter.convert(property, type);
    }

    public static String requireProperty(String key, String... args) {
        String property = requireProperty(key);
        return String.format(property, (Object[]) args);
    }

    @Nullable
    public static String loadProperty(String key) {
        try {
            FileInputStream in = new FileInputStream("src/main/resources/settings.properties");
            Properties properties = new Properties();
            properties.load(in);
            in.close();

            return properties.getProperty(key);
        } catch (IOException e) {
            throw new RuntimeException("Exception while loading property " + key, e);
        }
    }

    @Nullable
    public static <E> E loadProperty(Class<E> type, String key) {
        String property = loadProperty(key);

        if (property != null) {
            return StringConverter.convert(property, type);
        }

        return null;
    }

    @Nullable
    public static String loadProperty(String key, String... args) {
        String property = loadProperty(key);

        if (property != null) {
            return String.format(property, (Object[]) args);
        }

        return null;
    }

    public static boolean loadBoolProperty(String key) {
        String property = requireProperty(key);

        if (property.equalsIgnoreCase("true") || property.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(property);
        } else {
            throw new IllegalStateException("Property " + key + " not a boolean");
        }
    }

}
