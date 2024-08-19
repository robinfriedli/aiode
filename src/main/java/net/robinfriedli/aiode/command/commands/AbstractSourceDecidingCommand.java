package net.robinfriedli.aiode.command.commands;

import java.util.Arrays;
import java.util.Optional;

import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.discord.property.AbstractGuildProperty;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;

/**
 * Extension for commands that can search both YouTube and Spotify (e.g. the play, queue, add and search commands)
 * that returns the selected source based on the used arguments and the defaultSource property.
 */
public abstract class AbstractSourceDecidingCommand extends AbstractCommand {

    private static final Source DEFAULT_FALLBACK = Source.SPOTIFY;
    private static final Source DEFAULT_LIST_FALLBACK = Source.LOCAL;

    protected AbstractSourceDecidingCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    protected Source getSource() {
        if (argumentSet("spotify")) {
            return Source.SPOTIFY;
        } else if (argumentSet("youtube")) {
            return Source.YOUTUBE;
        } else if (argumentSet("local")) {
            return Source.LOCAL;
        } else if (argumentSet("soundcloud")) {
            return Source.SOUNDCLOUD;
        } else if (argumentSet("filebroker")) {
            return Source.FILEBROKER;
        } else {
            return getDefaultSource();
        }
    }

    private Source getDefaultSource() {
        GuildPropertyManager guildPropertyManager = Aiode.get().getGuildPropertyManager();
        if (argumentSet("list")) {
            AbstractGuildProperty defaultListSourceProperty = guildPropertyManager.getProperty("defaultListSource");
            if (defaultListSourceProperty != null) {
                return getSourceForProperty(defaultListSourceProperty).orElse(DEFAULT_LIST_FALLBACK);
            }

            return DEFAULT_LIST_FALLBACK;
        } else {
            AbstractGuildProperty defaultSourceProperty = guildPropertyManager.getProperty("defaultSource");
            if (defaultSourceProperty != null) {
                return getSourceForProperty(defaultSourceProperty).orElse(DEFAULT_FALLBACK);
            }

            return DEFAULT_FALLBACK;
        }
    }

    private Optional<Source> getSourceForProperty(AbstractGuildProperty property) {
        return Arrays.stream(Source.values())
            .filter(source -> source.name().equals(property.get())).findAny();
    }

    protected enum Source {

        SPOTIFY,
        YOUTUBE,
        LOCAL,
        SOUNDCLOUD,
        FILEBROKER;

        public boolean isSpotify() {
            return this == SPOTIFY;
        }

        public boolean isYouTube() {
            return this == YOUTUBE;
        }

        public boolean isLocal() {
            return this == LOCAL;
        }

        public boolean isSoundCloud() {
            return this == SOUNDCLOUD;
        }

        public boolean isFilebroker() {
            return this == FILEBROKER;
        }
    }

}
