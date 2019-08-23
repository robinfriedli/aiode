package net.robinfriedli.botify.command.commands;

import java.util.Arrays;
import java.util.Optional;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

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
        } else {
            return getDefaultSource();
        }
    }

    private Source getDefaultSource() {
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
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

        SPOTIFY(true, false, false),
        YOUTUBE(false, true, false),
        LOCAL(false, false, true);

        private final boolean isSpotify;
        private final boolean isYouTube;
        private final boolean isLocal;

        Source(boolean isSpotify, boolean isYouTube, boolean isLocal) {
            this.isSpotify = isSpotify;
            this.isYouTube = isYouTube;
            this.isLocal = isLocal;
        }

        public boolean isSpotify() {
            return isSpotify;
        }

        public boolean isYouTube() {
            return isYouTube;
        }

        public boolean isLocal() {
            return isLocal;
        }
    }

}
