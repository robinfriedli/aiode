package net.robinfriedli.aiode.util;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.google.api.client.util.Sets;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.boot.VersionManager;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.SecurityManager;
import net.robinfriedli.aiode.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.function.HibernateInvoker;
import net.robinfriedli.aiode.login.LoginManager;
import net.robinfriedli.jxp.api.JxpBackend;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import se.michaelthelin.spotify.SpotifyApi;

import static net.robinfriedli.aiode.util.ClassDescriptorNode.*;

/**
 * Service to handle custom constructor injection. #get is used to inject non-spring aiode components. This checks for
 * custom Aiode extractors and spring beans.
 */
public class InjectorService {

    private static final Set<Extractor<?>> EXTRACTORS = Sets.newHashSet();

    static {
        EXTRACTORS.add(new Extractor<>(AudioManager.class, () -> Aiode.get().getAudioManager()));
        EXTRACTORS.add(new Extractor<>(AudioPlayback.class, () -> ExecutionContext.Current.optional().map(ctx -> ctx.getGuildContext().getPlayback()).orElse(null)));
        EXTRACTORS.add(new Extractor<>(ExecutionContext.class, ExecutionContext.Current::get));
        EXTRACTORS.add(new Extractor<>(CommandExecutionQueueManager.class, () -> Aiode.get().getExecutionQueueManager()));
        EXTRACTORS.add(new Extractor<>(CommandManager.class, () -> Aiode.get().getCommandManager()));
        EXTRACTORS.add(new Extractor<>(GuildContext.class, () -> ExecutionContext.Current.optional().map(ExecutionContext::getGuildContext).orElse(null)));
        EXTRACTORS.add(new Extractor<>(GuildManager.class, () -> Aiode.get().getGuildManager()));
        EXTRACTORS.add(new Extractor<>(GuildPropertyManager.class, () -> Aiode.get().getGuildPropertyManager()));
        EXTRACTORS.add(new Extractor<>(HibernateInvoker.class, HibernateInvoker::new));
        EXTRACTORS.add(new Extractor<>(ShardManager.class, () -> Aiode.get().getShardManager()));
        EXTRACTORS.add(new Extractor<>(JxpBackend.class, () -> Aiode.get().getJxpBackend()));
        EXTRACTORS.add(new Extractor<>(Logger.class, () -> Aiode.LOGGER));
        EXTRACTORS.add(new Extractor<>(LoginManager.class, () -> Aiode.get().getLoginManager()));
        EXTRACTORS.add(new Extractor<>(MessageChannel.class, () -> ExecutionContext.Current.optional().map(ExecutionContext::getChannel).orElse(null)));
        EXTRACTORS.add(new Extractor<>(MessageService.class, () -> Aiode.get().getMessageService()));
        EXTRACTORS.add(new Extractor<>(SecurityManager.class, () -> Aiode.get().getSecurityManager()));
        EXTRACTORS.add(new Extractor<>(SessionFactory.class, () -> Aiode.get().getSessionFactory()));
        EXTRACTORS.add(new Extractor<>(SpotifyApi.class, () -> Aiode.get().getSpotifyApiBuilder().build()));
        EXTRACTORS.add(new Extractor<>(SpotifyApi.Builder.class, () -> Aiode.get().getSpotifyApiBuilder()));
        EXTRACTORS.add(new Extractor<>(YouTubeService.class, () -> Aiode.get().getAudioManager().getYouTubeService()));
        EXTRACTORS.add(new Extractor<>(VersionManager.class, () -> Aiode.get().getVersionManager()));
        EXTRACTORS.add(new Extractor<>(CommandContext.class, () -> ExecutionContext.Current.getUnwrap(CommandContext.class)));
    }

    @SuppressWarnings("unchecked")
    public static <E> E get(Class<E> type) {
        Set<Extractor<?>> extractors = EXTRACTORS.stream().filter(e -> type.isAssignableFrom(e.getType())).collect(Collectors.toSet());
        Extractor<?> extractor = selectClosestNode(extractors, type);
        if (extractor != null) {
            return (E) extractor.getSupplier().get();
        } else {
            ApplicationContext springBootContext = Aiode.get().getSpringBootContext();
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
