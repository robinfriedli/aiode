package net.robinfriedli.botify.discord;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.YouTubeService;
import net.robinfriedli.botify.command.AbstractWidget;
import net.robinfriedli.botify.command.CommandContext;
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
    private final GuildManager guildManager;
    private final DiscordBotListAPI discordBotListAPI;
    private final Logger logger;
    private Mode mode;

    public DiscordListener(JxpBackend jxpBackend,
                           SpotifyApi.Builder spotfiyApiBuilder,
                           SessionFactory sessionFactory,
                           LoginManager loginManager,
                           YouTubeService youTubeService,
                           DiscordBotListAPI discordBotListAPI) {
        this.jxpBackend = jxpBackend;
        this.spotfiyApiBuilder = spotfiyApiBuilder;
        this.sessionFactory = sessionFactory;
        this.discordBotListAPI = discordBotListAPI;
        Context guildSpecificationContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("GUILD_SPECIFICATION_PATH"));
        guildManager = new GuildManager(guildSpecificationContext);
        Context commandContributionContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("COMMANDS_PATH"));
        Context commandInterceptorContext = jxpBackend.getContext(PropertiesLoadingService.requireProperty("COMMAND_INTERCEPTORS_PATH"));
        commandManager = new CommandManager(this, loginManager, commandContributionContext, commandInterceptorContext, sessionFactory);
        audioManager = new AudioManager(youTubeService, sessionFactory, commandManager, guildManager);
        this.logger = LoggerFactory.getLogger(getClass());
    }

    public void setupGuilds(Mode mode, List<Guild> guilds) {
        this.mode = mode;
        for (Guild guild : guilds) {
            guildManager.addGuild(guild);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getAuthor().isBot() && event.getGuild() != null) {
            Guild guild = event.getGuild();
            Message message = event.getMessage();
            String msg = message.getContentDisplay();
            GuildContext guildContext = guildManager.getContextForGuild(guild);
            String botName = guildContext.getSpecification().getName();
            String prefix = guildContext.getSpecification().getPrefix();

            String lowerCaseMsg = msg.toLowerCase();
            boolean startsWithPrefix = !Strings.isNullOrEmpty(prefix) && lowerCaseMsg.startsWith(prefix.toLowerCase());
            boolean startsWithName = !Strings.isNullOrEmpty(botName) && lowerCaseMsg.startsWith(botName.toLowerCase());
            if (startsWithPrefix || startsWithName || lowerCaseMsg.startsWith("$botify")) {
                // specify with which part of the input string the bot was referenced, this helps trimming the command later
                String namePrefix;
                if (lowerCaseMsg.startsWith("$botify")) {
                    namePrefix = "$botify";
                } else {
                    if (startsWithName && startsWithPrefix) {
                        if (prefix.equals(botName) || prefix.length() > botName.length()) {
                            namePrefix = prefix;
                        } else {
                            namePrefix = botName;
                        }
                    } else if (startsWithName) {
                        namePrefix = botName;
                    } else {
                        namePrefix = prefix;
                    }
                }

                MessageService messageService = new MessageService();
                if (namePrefix == null) {
                    messageService.sendException("Something went wrong parsing your command, try starting with \"$botify\" instead.", message.getChannel());
                    logger.error("Name prefix null for input " + msg + ". Bot name: " + botName + "; Prefix: " + prefix);
                }
                assert namePrefix != null;
                CommandContext commandContext = new CommandContext(namePrefix, message, sessionFactory, spotfiyApiBuilder.build(), guildContext);
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
        MessageService messageService = new MessageService();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("**Getting started**");
        embedBuilder.setDescription("A brief introduction to Botify and the most important commands.");
        embedBuilder.addField("Commands: The principle", "All commands follow the same structure: " +
            "[bot name, custom prefix or $botify] [arguments (start with $)] [input text]. " +
            "E.g. mybot play $spotify $album meteora.", false);
        embedBuilder.addField("Commands: The essentials", "The most essential command is the play command. " +
            "With the $spotify or $youtube arguments you can search a track or video from the corresponding platform or " +
            "you can simply paste a link." + System.lineSeparator() + "The queue command displays the current tracks or " +
            "adds elements to the queue." + System.lineSeparator() + "Also crucial to know is the answer command. " +
            "Commands like the play or queue command might find several results when searching for tracks on Spotify, " +
            "in which case the bot displays a list of options. Each option is usually mapped to a number. " +
            "An option can be chosen like this: mybot answer 2.", false);
        embedBuilder.addField("Playback management", "The playback can be managed by using the controls shown " +
            "by the queue command or \"Now playing...\" messages in the form of reactions or by using the commands in " +
            "the playback management category (stop / pause / skip / forward)", false);
        embedBuilder.addField("Playlist management", "Create cross-platform playlists with tracks from any " +
            "source using the create command and add tracks using the add command or export the current queue to a new" +
            "playlist using the export command. For more info see the commands in the playlist management category.", false);
        embedBuilder.addField("Getting help", "The help command shows all available commands or provides help " +
            "with a specific command (e.g. mybot help play).", false);
        try {
            messageService.sendWithLogo(embedBuilder, guild);
        } catch (Throwable e) {
            logger.error("Error sending getting started message", e);
        }

        guildManager.addGuild(guild);

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
                        new MessageService().sendError(e.getMessage(), channel);
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

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        commandManager.getActiveWidget(event.getMessageId()).ifPresent(widget -> widget.setMessageDeleted(true));
    }

    public GuildManager getGuildManager() {
        return guildManager;
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
