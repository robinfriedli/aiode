package net.robinfriedli.botify.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import com.google.common.base.Strings;

public class PropertiesLoadingService {

    public static String requireProperty(String key) {
        String property = loadProperty(key);
        if (!Strings.isNullOrEmpty(property)) {
            return property;
        } else {
            throw new IllegalStateException("Property " + key + " not set");
        }
    }

    public static String requireProperty(String key, String... args) {
        String property = loadProperty(key, args);
        if (!Strings.isNullOrEmpty(property)) {
            return property;
        } else {
            throw new IllegalStateException("Property " + key + " not set");
        }
    }

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

    public static String loadProperty(String key, String... args) {
        try {
            FileInputStream in = new FileInputStream("./resources/settings.properties");
            Properties properties = new Properties();
            properties.load(in);
            in.close();

            return String.format(properties.getProperty(key), (Object[]) args);
        } catch (IOException e) {
            throw new RuntimeException("Exception while loading property " + key, e);
        }
    }

    public static boolean loadBoolProperty(String key) {
        try {
            FileInputStream in = new FileInputStream("./resources/settings.properties");
            Properties properties = new Properties();
            properties.load(in);
            in.close();
            String property = properties.getProperty(key);

            if (property != null) {
                if (property.equalsIgnoreCase("true") || property.equalsIgnoreCase("false")) {
                    return Boolean.parseBoolean(property);
                } else {
                    throw new IllegalStateException("Property " + key + " not a boolean");
                }
            } else {
                throw new IllegalStateException("Property " + key + " not set");
            }
        } catch (IOException e) {
            throw new RuntimeException("Exception while loading property " + key, e);
        }
    }

}
