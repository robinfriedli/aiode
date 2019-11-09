package net.robinfriedli.botify.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.annotation.Nullable;

import com.google.common.base.Strings;
import net.robinfriedli.jxp.api.StringConverter;

public class PropertiesLoadingService {

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
            FileInputStream in = new FileInputStream("./resources/settings.properties");
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
