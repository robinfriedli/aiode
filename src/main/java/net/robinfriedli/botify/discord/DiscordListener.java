package net.robinfriedli.botify.discord;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;

import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandExecutor;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.exceptions.CommandExceptionHandler;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.UserException;
import net.robinfriedli.botify.exceptions.WidgetExceptionHandler;
import net.robinfriedli.botify.listener.InterceptorChain;
import net.robinfriedli.botify.listener.PlaylistItemTimestampListener;
import net.robinfriedli.botify.login.LoginManager;
import net.robinfriedli.botify.util.HibernatePlaylistMigrator;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.discordbots.api.client.DiscordBotListAPI;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class DiscordListener extends ListenerAdapter {

    private final AudioManager audioManager;
    private final JxpBackend jxpBackend;
    private final SpotifyApi.Builder spotfiyApiBuilder;
    private final SessionFactory sessionFactory;
    private final CommandManager commandManager;
    private final GuildSpecificationManager guildSpecificationManager;
    private final DiscordBotListAPI discordBotListAPI;
    private final Logger logger;
    private Mode mode;

    public DiscordListener(JxpBackend jxpBackend,
                           SpotifyApi.Builder spotfiyApiBuilder,
                           SessionFactory sessionFactory,
                           LoginManager loginManager,
                           YouTubeService youTubeService,
                           Logger logger,
                           DiscordBotListAPI discordBotListAPI) {
        this.jxpBackend = jxpBackend;
        this.spotfiyApiBuilder = spotfiyApiBuilder;
        this.sessionFactory = sessionFactory;
        this.discordBotListAPI = discordBotListAPI;
        Context guildSpecificationContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("GUILD_SPECIFICATION_PATH"));
        guildSpecificationManager = new GuildSpecificationManager(guildSpecificationContext);
        Context commandContributionContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("COMMANDS_PATH"));
        Context commandInterceptorContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("COMMAND_INTERCEPTORS_PATH"));
        commandManager = new CommandManager(new CommandExecutor(loginManager, logger), this, loginManager, commandContributionContext, commandInterceptorContext, logger);
        audioManager = new AudioManager(youTubeService, logger, sessionFactory, commandManager);
        this.logger = logger;
    }

    public void setupGuilds(Mode mode, List<Guild> guilds) {
        this.mode = mode;
        for (Guild guild : guilds) {
            guildSpecificationManager.addGuild(guild);
            audioManager.addGuild(guild);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getAuthor().isBot() && event.getGuild() != null) {
            Guild guild = event.getGuild();
            Message message = event.getMessage();
            String msg = message.getContentDisplay();
            String botName = guildSpecificationManager.getNameForGuild(guild);

            if ((!Strings.isNullOrEmpty(botName) && msg.toLowerCase().startsWith(botName.toLowerCase()))
                || msg.startsWith("$botify")) {
                // specify with which part of the input string the bot was referenced, this helps trimming the command later
                String namePrefix;
                if (msg.startsWith("$botify")) {
                    namePrefix = "$botify";
                } else {
                    namePrefix = botName;
                }

                MessageService messageService = new MessageService();
                CommandContext commandContext = new CommandContext(namePrefix, message, sessionFactory, spotfiyApiBuilder.build());
                Thread commandExecutionThread = new Thread(() -> {
                    try {
                        commandManager.runCommand(commandContext);
                    } catch (UserException e) {
                        messageService.sendError(e.getMessage(), message.getChannel());
                    } finally {
                        commandContext.closeSession();
                    }
                });

                commandExecutionThread.setUncaughtExceptionHandler(new CommandExceptionHandler(commandContext, logger));
                commandExecutionThread.setName("botify command execution: " + commandContext);
                commandExecutionThread.start();

                Thread monitoringThread = new Thread(() -> {
                    try {
                        commandExecutionThread.join(5000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (commandExecutionThread.isAlive()) {
                        messageService.send("Still loading...", message.getChannel());
                    }
                });

                commandContext.registerMonitoring(monitoringThread);
                monitoringThread.start();
            }
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        guildSpecificationManager.addGuild(guild);
        audioManager.addGuild(guild);

        if (discordBotListAPI != null) {
            discordBotListAPI.setStats(event.getJDA().getGuilds().size());
        }

        try (Session session = sessionFactory.withOptions().interceptor(InterceptorChain.of(PlaylistItemTimestampListener.class)).openSession()) {
            String playlistsPath = PropertiesLoadingService.requireProperty("PLAYLISTS_PATH");
            File file = new File(playlistsPath);
            if (file.exists()) {
                Context context = jxpBackend.getContext(file);
                HibernatePlaylistMigrator hibernatePlaylistMigrator = new HibernatePlaylistMigrator();
                Map<Playlist, List<PlaylistItem>> playlistMap;
                try {
                    playlistMap = hibernatePlaylistMigrator.doMigrate(context, guild, session, spotfiyApiBuilder.build());
                } catch (IOException | SpotifyWebApiException e) {
                    logger.error("Exception while migrating hibernate playlists", e);
                    session.close();
                    return;
                }

                session.beginTransaction();
                for (Playlist playlist : playlistMap.keySet()) {
                    Playlist existingList = SearchEngine.searchLocalList(session, playlist.getName(), mode == Mode.PARTITIONED, guild.getId());
                    if (existingList == null) {
                        session.persist(playlist);
                        playlistMap.get(playlist).forEach(session::persist);
                    }
                }
                session.getTransaction().commit();
            }
        }
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        Guild guild = event.getGuild();
        audioManager.removeGuild(guild);

        if (discordBotListAPI != null) {
            discordBotListAPI.setStats(event.getJDA().getGuilds().size());
        }
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (!event.getUser().isBot()) {
            String messageId = event.getMessageId();

            Optional<AbstractWidget> activeWidget = commandManager.getActiveWidget(messageId);
            if (activeWidget.isPresent()) {
                TextChannel channel = event.getChannel();
                Thread widgetExecutionThread = new Thread(() -> {
                    try {
                        activeWidget.get().handleReaction(event);
                    } catch (UserException e) {
                        new MessageService().send(e.getMessage(), channel);
                    } catch (Exception e) {
                        throw new CommandRuntimeException(e);
                    }
                });
                widgetExecutionThread.setName("Widget execution thread " + messageId);
                widgetExecutionThread.setUncaughtExceptionHandler(new WidgetExceptionHandler(channel, logger));
                widgetExecutionThread.start();
            }
        }
    }

    public GuildSpecificationManager getGuildSpecificationManager() {
        return guildSpecificationManager;
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    public JxpBackend getJxpBackend() {
        return jxpBackend;
    }

    public Mode getMode() {
        return mode;
    }

    public Logger getLogger() {
        return logger;
    }

    public enum Mode {
        /**
         * all guilds share the same {@link Context} meaning all playlists will be available across all guilds
         */
        SHARED,

        /**
         * there will be a separate {@link Context.BindableContext} for each guild meaning the playlists will be separated
         * <p>
         * Recommended if one botify instance is shared by completely different guilds
         */
        PARTITIONED
    }

}
