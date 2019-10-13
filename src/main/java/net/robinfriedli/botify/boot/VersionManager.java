package net.robinfriedli.botify.boot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.robinfriedli.botify.entities.xml.Version;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Manager to access Botify release versions
 */
public class VersionManager {

    private final Context context;

    public VersionManager(Context context) {
        this.context = context;
    }

    /**
     * Compare two versions to determine the lower and later versions
     *
     * @param version1 the first version
     * @param version2 the second verion
     * @return -1 if version1 is lower (e.g. 1.4), +1 if version1 is higher (e.g. 1.6) or 0 if they are equal
     */
    public static int compareVersionString(String version1, String version2) {
        String[] split1 = version1.split("\\.");
        String[] split2 = version2.split("\\.");

        for (int i = 0; i < split1.length; i++) {
            if (i > split2.length - 1) {
                return 1;
            }

            int valueCompare = split1[i].compareTo(split2[i]);
            if (valueCompare != 0) {
                return valueCompare;
            }
        }

        if (split1.length < split2.length) {
            return -1;
        }

        return 0;
    }

    public Context getContext() {
        return context;
    }

    /**
     * @return the version element representing the current version
     */
    public Version getCurrentVersion() {
        return context.query(attribute("version").is(getCurrentVersionString()), Version.class).getOnlyResult();
    }

    public List<Version> getVersions() {
        return context.getInstancesOf(Version.class);
    }

    /**
     * @return the version element representing the current version and all lower versions in descending order
     */
    public List<Version> getCurrentVersionOrLower() {
        return context.query(
            and(
                instanceOf(Version.class),
                xmlElement -> 0 >= compareVersionString(xmlElement.getAttribute("version").getValue(), getCurrentVersionString())
            ), Version.class)
            .getResultStream()
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());
    }

    private String getCurrentVersionString() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader("./resources/current-version.txt"));
            return bufferedReader.readLine();
        } catch (IOException e) {
            throw new RuntimeException("IOException occurred while trying to read current version", e);
        }
    }

    /**
     * Additional conditions to use in JXP predicate queries
     */
    public static class Conditions {

        public static Predicate<XmlElement> lowerVersionThan(String version) {
            return xmlElement -> 0 > compareVersionString(xmlElement.getAttribute("version").getValue(), version);
        }

        public static Predicate<XmlElement> lowerOrEqualVersionAs(String version) {
            return xmlElement -> 0 >= compareVersionString(xmlElement.getAttribute("version").getValue(), version);
        }

        public static Predicate<XmlElement> greaterVersionThan(String version) {
            return xmlElement -> 0 < compareVersionString(xmlElement.getAttribute("version").getValue(), version);
        }

        public static Predicate<XmlElement> greaterOrEqualVersionAs(String version) {
            return xmlElement -> 0 <= compareVersionString(xmlElement.getAttribute("version").getValue(), version);
        }

    }

}
