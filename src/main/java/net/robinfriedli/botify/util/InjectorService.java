package net.robinfriedli.botify.util;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.google.api.client.util.Sets;
import com.wrapper.spotify.SpotifyApi;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.boot.VersionManager;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.jxp.api.JxpBackend;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import static net.robinfriedli.botify.util.ClassDescriptorNode.*;

/**
 * Service to handle custom constructor injection. #get is used to inject non-spring botify components. This checks for
 * custom Botify extractors and spring beans.
 */
public class InjectorService {

    private static final Set<Extractor<?>> EXTRACTORS = Sets.newHashSet();

    static {
        EXTRACTORS.add(new Extractor<>(AudioManager.class, () -> Botify.get().getAudioManager()));
        EXTRACTORS.add(new Extractor<>(AudioPlayback.class, () -> ExecutionContext.Current.optional().map(ctx -> ctx.getGuildContext().getPlayback()).orElse(null)));
        EXTRACTORS.add(new Extractor<>(ExecutionContext.class, ExecutionContext.Current::get));
        EXTRACTORS.add(new Extractor<>(CommandExecutionQueueManager.class, () -> Botify.get().getExecutionQueueManager()));
        EXTRACTORS.add(new Extractor<>(CommandManager.class, () -> Botify.get().getCommandManager()));
        EXTRACTORS.add(new Extractor<>(GuildContext.class, () -> ExecutionContext.Current.optional().map(ExecutionContext::getGuildContext).orElse(null)));
        EXTRACTORS.add(new Extractor<>(GuildManager.class, () -> Botify.get().getGuildManager()));
        EXTRACTORS.add(new Extractor<>(GuildPropertyManager.class, () -> Botify.get().getGuildPropertyManager()));
        EXTRACTORS.add(new Extractor<>(ShardManager.class, () -> Botify.get().getShardManager()));
        EXTRACTORS.add(new Extractor<>(JxpBackend.class, () -> Botify.get().getJxpBackend()));
        EXTRACTORS.add(new Extractor<>(Logger.class, () -> Botify.LOGGER));
        EXTRACTORS.add(new Extractor<>(LoginManager.class, () -> Botify.get().getLoginManager()));
        EXTRACTORS.add(new Extractor<>(MessageChannel.class, () -> ExecutionContext.Current.optional().map(ExecutionContext::getChannel).orElse(null)));
        EXTRACTORS.add(new Extractor<>(MessageService.class, () -> Botify.get().getMessageService()));
        EXTRACTORS.add(new Extractor<>(SecurityManager.class, () -> Botify.get().getSecurityManager()));
        EXTRACTORS.add(new Extractor<>(SessionFactory.class, () -> Botify.get().getSessionFactory()));
        EXTRACTORS.add(new Extractor<>(SpotifyApi.class, () -> Botify.get().getSpotifyApiBuilder().build()));
        EXTRACTORS.add(new Extractor<>(SpotifyApi.Builder.class, () -> Botify.get().getSpotifyApiBuilder()));
        EXTRACTORS.add(new Extractor<>(YouTubeService.class, () -> Botify.get().getAudioManager().getYouTubeService()));
        EXTRACTORS.add(new Extractor<>(VersionManager.class, () -> Botify.get().getVersionManager()));
        EXTRACTORS.add(new Extractor<>(CommandContext.class, () -> ExecutionContext.Current.getUnwrap(CommandContext.class)));
    }

    @SuppressWarnings("unchecked")
    public static <E> E get(Class<E> type) {
        Set<Extractor<?>> extractors = EXTRACTORS.stream().filter(e -> type.isAssignableFrom(e.getType())).collect(Collectors.toSet());
        Extractor<?> extractor = selectClosestNode(extractors, type);
        if (extractor != null) {
            return (E) extractor.getSupplier().get();
        } else {
            ApplicationContext springBootContext = Botify.get().getSpringBootContext();
            ObjectProvider<E> beanProvider = springBootContext.getBeanProvider(type);
            return beanProvider.getIfAvailable();
        }
    }

    private static class Extractor<E> implements ClassDescriptorNode {

        private final Class<E> type;
        private final Supplier<E> supplier;

        private Extractor(Class<E> type, Supplier<E> supplier) {
            this.type = type;
            this.supplier = supplier;
        }

        @Override
        public Class<E> getType() {
            return type;
        }

        Supplier<E> getSupplier() {
            return supplier;
        }
    }

}
