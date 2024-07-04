package net.robinfriedli.aiode.discord.listeners;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.SecurityManager;
import net.robinfriedli.aiode.concurrent.EventHandlerPool;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.concurrent.ThreadContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.StoredScript;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import net.robinfriedli.aiode.scripting.GroovyVariableManager;
import net.robinfriedli.aiode.scripting.SafeGroovyScriptRunner;
import net.robinfriedli.aiode.scripting.ScriptCommandRunner;
import net.robinfriedli.exec.MutexSync;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import se.michaelthelin.spotify.SpotifyApi;

/**
 * Listener responsible for listening for VoiceChannel events; currently used for the auto pause feature
 */
@Component
public class VoiceChannelListener extends ListenerAdapter {

    private final AudioManager audioManager;
    private final CommandManager commandManager;
    private final GroovySandboxComponent groovySandboxComponent;
    private final GroovyVariableManager groovyVariableManager;
    private final GuildManager guildManager;
    private final HibernateComponent hibernateComponent;
    private final MutexSync<Long> mutexSync = new MutexSync<>();
    private final QueryBuilderFactory queryBuilderFactory;
    private final SecurityManager securityManager;
    private final SpotifyApi.Builder spotifyApiBuilder;

    public VoiceChannelListener(
        AudioManager audioManager,
        CommandManager commandManager,
        GroovySandboxComponent groovySandboxComponent,
        GroovyVariableManager groovyVariableManager,
        GuildManager guildManager,
        HibernateComponent hibernateComponent,
        QueryBuilderFactory queryBuilderFactory,
        SecurityManager securityManager,
        SpotifyApi.Builder spotifyApiBuilder
    ) {
        this.audioManager = audioManager;
        this.commandManager = commandManager;
        this.groovySandboxComponent = groovySandboxComponent;
        this.groovyVariableManager = groovyVariableManager;
        this.guildManager = guildManager;
        this.hibernateComponent = hibernateComponent;
        this.queryBuilderFactory = queryBuilderFactory;
        this.securityManager = securityManager;
        this.spotifyApiBuilder = spotifyApiBuilder;
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        AudioChannel oldValue = event.getOldValue();
        AudioChannel newValue = event.getNewValue();
        if (oldValue != null) {
            onGuildVoiceLeave(event, event.getMember(), oldValue);
        }
        if (newValue != null) {
            onGuildVoiceJoin(event, event.getMember(), newValue);
        }
    }

    private void onGuildVoiceLeave(GuildVoiceUpdateEvent event, Member member, AudioChannel channelLeft) {
        if (!member.getUser().isBot()) {
            Guild guild = event.getGuild();
            EventHandlerPool.execute(() -> mutexSync.run(guild.getIdLong(), () -> {
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);

                if (channelLeft.equals(playback.getAudioChannel())
                    && noOtherMembersLeft(channelLeft, guild)) {
                    if (isAutoPauseEnabled(guild)) {
                        playback.pause();
                        playback.leaveChannel();
                    } else {
                        playback.setAloneSince(LocalDateTime.now());
                    }
                }

                ExecutionContext executionContext = new ExecutionContext(
                    guild,
                    guildManager.getContextForGuild(guild),
                    event.getJDA(),
                    event.getMember(),
                    hibernateComponent.getSessionFactory(),
                    spotifyApiBuilder,
                    Optional
                        .ofNullable(playback.getCommunicationChannel())
                        .orElseGet(() -> guildManager.getDefaultTextChannelForGuild(guild))
                );
                ExecutionContext.Current.set(executionContext);
                runScriptTriggers("voiceleave", channelLeft, executionContext);
            }));
        }
    }

    private void onGuildVoiceJoin(GuildVoiceUpdateEvent event, Member member, AudioChannel channelJoined) {
        if (!member.getUser().isBot()) {
            Guild guild = event.getGuild();
            EventHandlerPool.execute(() -> mutexSync.run(guild.getIdLong(), () -> {
                AudioPlayback playback = audioManager.getPlaybackForGuild(guild);

                if (channelJoined.equals(playback.getAudioChannel())) {
                    playback.setAloneSince(null);
                }

                ExecutionContext executionContext = new ExecutionContext(
                    guild,
                    guildManager.getContextForGuild(guild),
                    event.getJDA(),
                    event.getMember(),
                    hibernateComponent.getSessionFactory(),
                    spotifyApiBuilder,
                    Optional
                        .ofNullable(playback.getCommunicationChannel())
                        .orElseGet(() -> guildManager.getDefaultTextChannelForGuild(guild))
                );
                ExecutionContext.Current.set(executionContext);
                runScriptTriggers("voicejoin", channelJoined, executionContext);
            }));
        }
    }

    private boolean noOtherMembersLeft(AudioChannel channel, Guild guild) {
        return channel.getMembers().stream()
            .allMatch(member -> member.equals(guild.getSelfMember()) || member.getUser().isBot());
    }

    private boolean isAutoPauseEnabled(Guild guild) {
        return hibernateComponent.invokeWithSession(session -> {
            GuildPropertyManager guildPropertyManager = Aiode.get().getGuildPropertyManager();
            GuildManager guildManager = Aiode.get().getGuildManager();
            GuildSpecification specification = guildManager.getContextForGuild(guild).getSpecification(session);

            return guildPropertyManager
                .getPropertyValueOptional("enableAutoPause", Boolean.class, specification)
                .orElse(true);
        });
    }

    private void runScriptTriggers(String event, AudioChannel targetChannel, ExecutionContext executionContext) {
        List<StoredScript> triggerScripts = hibernateComponent.invokeWithSession(session ->
            queryBuilderFactory
                .find(StoredScript.class)
                .where((cb, root, subQueryFactory) -> cb.and(
                    cb.isTrue(root.get("active")),
                    cb.equal(root.get("triggerEvent"), event),
                    cb.equal(
                        root.get("scriptUsage").get("pk"),
                        subQueryFactory.createUncorrelatedSubQuery(StoredScript.ScriptUsage.class, "pk", Long.class)
                            .where((cb1, root1) -> cb1.equal(root1.get("uniqueId"), "trigger"))
                            .build(session)
                    )
                ))
                .build(session)
                .getResultList()
        );

        if (!triggerScripts.isEmpty()) {
            SafeGroovyScriptRunner groovyScriptRunner = new SafeGroovyScriptRunner(
                executionContext,
                groovySandboxComponent,
                groovyVariableManager,
                securityManager,
                false
            );

            ThreadContext.Current.install(
                GroovyVariableManager.ADDITIONAL_VARIABLES_KEY,
                Map.of(
                    "voiceChannel", targetChannel,
                    "command", new ScriptCommandRunner(commandManager)
                )
            );
            groovyScriptRunner.runScripts(triggerScripts, "trigger");
        }
    }

}
