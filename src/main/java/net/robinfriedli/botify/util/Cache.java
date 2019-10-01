package net.robinfriedli.botify.util;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.jxp.api.JxpBackend;
import org.hibernate.SessionFactory;

public class Cache {

    private static final List<Extractor<?>> EXTRACTORS = Lists.newArrayList();
    private static final List<Object> CACHED_OBJECTS = Lists.newArrayList();

    static {
        EXTRACTORS.add(new Extractor<>(AudioManager.class, () -> Botify.get().getAudioManager()));
        EXTRACTORS.add(new Extractor<>(AudioPlayback.class, () -> {
            if (CommandContext.Current.isSet()) {
                return CommandContext.Current.require().getGuildContext().getPlayback();
            } else {
                return null;
            }
        }));
        EXTRACTORS.add(new Extractor<>(CommandContext.class, CommandContext.Current::get));
        EXTRACTORS.add(new Extractor<>(CommandExecutionQueueManager.class, () -> Botify.get().getExecutionQueueManager()));
        EXTRACTORS.add(new Extractor<>(CommandManager.class, () -> Botify.get().getCommandManager()));
        EXTRACTORS.add(new Extractor<>(GuildContext.class, () -> {
            if (CommandContext.Current.isSet()) {
                return CommandContext.Current.require().getGuildContext();
            } else {
                return null;
            }
        }));
        EXTRACTORS.add(new Extractor<>(GuildManager.class, () -> Botify.get().getGuildManager()));
        EXTRACTORS.add(new Extractor<>(GuildPropertyManager.class, () -> Botify.get().getGuildPropertyManager()));
        EXTRACTORS.add(new Extractor<>(JDA.class, () -> Botify.get().getJda()));
        EXTRACTORS.add(new Extractor<>(JxpBackend.class, () -> Botify.get().getJxpBackend()));
        EXTRACTORS.add(new Extractor<>(Logger.class, () -> Botify.LOGGER));
        EXTRACTORS.add(new Extractor<>(LoginManager.class, () -> Botify.get().getLoginManager()));
        EXTRACTORS.add(new Extractor<>(MessageChannel.class, () -> {
            if (CommandContext.Current.isSet()) {
                return CommandContext.Current.require().getChannel();
            } else {
                return null;
            }
        }));
        EXTRACTORS.add(new Extractor<>(MessageService.class, () -> Botify.get().getMessageService()));
        EXTRACTORS.add(new Extractor<>(SecurityManager.class, () -> Botify.get().getSecurityManager()));
        EXTRACTORS.add(new Extractor<>(SessionFactory.class, () -> Botify.get().getSessionFactory()));
        EXTRACTORS.add(new Extractor<>(SpotifyApi.class, () -> Botify.get().getSpotifyApiBuilder().build()));
        EXTRACTORS.add(new Extractor<>(SpotifyApi.Builder.class, () -> Botify.get().getSpotifyApiBuilder()));
        EXTRACTORS.add(new Extractor<>(YouTubeService.class, () -> Botify.get().getAudioManager().getYouTubeService()));
    }

    @SuppressWarnings("unchecked")
    public static <E> E get(Class<E> type) {
        Optional<Object> cachedObject = CACHED_OBJECTS.stream().filter(o -> type.isAssignableFrom(o.getClass())).findAny();
        if (cachedObject.isPresent()) {
            return (E) cachedObject.get();
        } else {
            Optional<Extractor<?>> extractor = EXTRACTORS.stream().filter(e -> type.isAssignableFrom(e.getType())).findAny();
            return extractor.map(value -> (E) value.getSupplier().get()).orElse(null);
        }
    }

    public static void cacheObject(Object o) {
        CACHED_OBJECTS.add(o);
    }

    public static boolean removeObjet(Object o) {
        return CACHED_OBJECTS.remove(o);
    }

    private static class Extractor<E> {

        private final Class<E> type;
        private final Supplier<E> supplier;

        private Extractor(Class<E> type, Supplier<E> supplier) {
            this.type = type;
            this.supplier = supplier;
        }

        Class<E> getType() {
            return type;
        }

        Supplier<E> getSupplier() {
            return supplier;
        }
    }

}
