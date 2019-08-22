package net.robinfriedli.botify.exceptions;

import java.awt.Color;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import net.dv8tion.jda.api.EmbedBuilder;

public class ExceptionUtils {

    public static EmbedBuilder buildErrorEmbed(Throwable e) {
        Throwable exception = e instanceof CommandRuntimeException ? e.getCause() : e;
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.RED);
        appendException(embedBuilder, exception, false);
        return embedBuilder;
    }

    private static void appendException(EmbedBuilder embedBuilder, Throwable e, boolean isCause) {
        String message = e instanceof GoogleJsonResponseException
            ? ((GoogleJsonResponseException) e).getDetails().getMessage()
            : e.getMessage();
        embedBuilder.addField(isCause ? "Caused by" : "Exception", String.format("%s: %s", e.getClass().getSimpleName(), message), false);

        if (e.getCause() != null) {
            appendException(embedBuilder, e.getCause(), true);
        }
    }

}
