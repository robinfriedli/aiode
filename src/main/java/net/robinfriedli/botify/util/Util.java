package net.robinfriedli.botify.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;

/**
 * various static utility methods
 */
public class Util {

    /**
     * Return all keys of a map where the assigned value is equal to the provided value. Useful if a BidiMap can't be
     * used because the values are not unique.
     */
    public static <K, V> Set<K> getKeysForValue(Map<K, V> map, V value) {
        return map.keySet().stream().filter(key -> map.get(key).equals(value)).collect(Collectors.toSet());
    }

    /**
     * return the given amount of milliseconds as a human readable string with minutes and seconds: 03:14
     */
    public static String normalizeMillis(long millis) {
        // get amount of full hours
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        // get amount of full minutes and subtract the full hours
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours);
        // get amount of full seconds and subtract the full minutes and hours
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes);

        String minuteString = minutes < 10 ? "0" + minutes : String.valueOf(minutes);
        String secondString = seconds < 10 ? "0" + seconds : String.valueOf(seconds);

        if (hours > 0) {
            return String.format("%s:%s:%s", hours, minuteString, secondString);
        } else {
            return String.format("%s:%s", minuteString, secondString);
        }
    }

    public static <E> void appendEmbedList(EmbedBuilder embedBuilder, Collection<E> elements, Function<E, String> stringFunc, String title) {
        appendEmbedList(embedBuilder, elements, stringFunc, title, false);
    }

    public static <E> void appendEmbedList(EmbedBuilder embedBuilder, Collection<E> elements, Function<E, String> stringFunc, String title, boolean inline) {
        List<StringBuilder> parts = Lists.newArrayList(new StringBuilder());

        for (E element : elements) {
            StringBuilder currentPart = parts.get(parts.size() - 1);
            String toAppend = stringFunc.apply(element);
            if (currentPart.length() + toAppend.length() < 1000) {
                currentPart.append(toAppend).append(System.lineSeparator());
            } else {
                parts.add(new StringBuilder().append(toAppend).append(System.lineSeparator()));
            }
        }

        for (int i = 0; i < parts.size(); i++) {
            embedBuilder.addField(i == 0 ? title : "", parts.get(i).toString(), inline);
        }
    }

    public static String normalizeWhiteSpace(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

}
