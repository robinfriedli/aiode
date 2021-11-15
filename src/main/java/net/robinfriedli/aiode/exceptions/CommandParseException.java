package net.robinfriedli.aiode.exceptions;

import java.awt.Color;

import net.dv8tion.jda.api.EmbedBuilder;

/**
 * Exception class that signals that the user entered a command that could not be parsed due to user fault
 */
public class CommandParseException extends UserException {

    private final String errorMessage;
    private final String commandString;
    private final UserException cause;
    private final int index;

    public CommandParseException(String errorMessage, String commandString, UserException cause, int index) {
        super(errorMessage);
        this.errorMessage = errorMessage;
        this.commandString = commandString.replace('\n', ' ');
        this.cause = cause;
        this.index = index;
    }

    @Override
    public EmbedBuilder buildEmbed() {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Invalid command");

        StringBuilder builder = new StringBuilder(errorMessage);
        builder.append(System.lineSeparator()).append("```").append(System.lineSeparator());

        if (commandString.length() <= 50) {
            builder.append(commandString).append(System.lineSeparator())
                .append(" ".repeat(index)).append("^");
        } else {
            int beginIndex = 0;
            boolean beginOverflow = false;
            int endIndex = commandString.length();
            boolean endOverflow = false;

            if (index > 20) {
                beginIndex = index - 20;
                beginOverflow = true;
            }
            if (index < commandString.length() - 20) {
                endIndex = index + 20;
                endOverflow = true;
            }

            String commandPart = commandString.substring(beginIndex, endIndex);
            int actualPosition = index;
            if (beginOverflow) {
                builder.append("...");
                actualPosition = 23; // the 3 dots plus the 20 characters shown
            }
            builder.append(commandPart);
            if (endOverflow) {
                builder.append("...");
            }
            builder.append(System.lineSeparator()).append(" ".repeat(actualPosition)).append("^");
        }
        builder.append(System.lineSeparator()).append("```").append("Failed at: ").append(index);

        if (cause instanceof AdditionalInformationException) {
            builder.append(System.lineSeparator()).append(System.lineSeparator())
                .append("_").append(((AdditionalInformationException) cause).getAdditionalInformation()).append("_");
        }

        embedBuilder.setColor(Color.RED);
        embedBuilder.setDescription(builder.toString());
        return embedBuilder;
    }
}
