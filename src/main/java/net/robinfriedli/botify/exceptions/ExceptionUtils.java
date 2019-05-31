package net.robinfriedli.botify.exceptions;

import java.awt.Color;

import net.dv8tion.jda.core.EmbedBuilder;

class ExceptionUtils {

    static EmbedBuilder buildErrorEmbed(Throwable e) {
        Throwable exception = e instanceof CommandRuntimeException ? e.getCause() : e;
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.RED);
        embedBuilder.addField("Exception", String.format("%s: %s", exception.getClass().getSimpleName(), exception.getMessage()), false);
        recursiveCause(embedBuilder, exception);
        return embedBuilder;
    }

    private static void recursiveCause(EmbedBuilder embedBuilder, Throwable exception) {
        Throwable cause = exception.getCause();
        if (cause != null) {
            embedBuilder.addField("Caused by", String.format("%s: %s", cause.getClass().getSimpleName(), cause.getMessage()), false);
            recursiveCause(embedBuilder, cause);
        }
    }

}
