package net.robinfriedli.botify.command.commands.admin;

import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.ISnowflake;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GrantedRole;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.PlaylistItem;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import org.hibernate.Session;

public class CleanDbCommand extends AbstractAdminCommand {

    public CleanDbCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description);
    }

    @Override
    public void runAdmin() {
        Botify.shutdownListeners();
        try {
            if (!argumentSet("silent")) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("Scheduled cleanup");
                embedBuilder.setDescription("Botify is suspending for a few seconds to clean the database.");
                sendToActiveGuilds(embedBuilder.build());
            }

            CommandExecutionQueueManager executionQueueManager = Botify.get().getExecutionQueueManager();
            CommandContext context = getContext();
            Thread cleanupThread = new Thread(() -> {
                CommandContext.Current.set(context);
                try {
                    Thread joiningThread = new Thread(() -> {
                        try {
                            executionQueueManager.joinAll();
                        } catch (InterruptedException ignored) {
                        }
                    });
                    joiningThread.start();
                    try {
                        joiningThread.join(60000);
                        joiningThread.interrupt();
                    } catch (InterruptedException ignored) {
                    }

                    doClean();
                } finally {
                    Botify.registerListeners();
                }
            });
            cleanupThread.setName("Botify database cleanup thread");
            Thread.UncaughtExceptionHandler commandExecutionExceptionHandler = Thread.currentThread().getUncaughtExceptionHandler();
            if (commandExecutionExceptionHandler != null) {
                cleanupThread.setUncaughtExceptionHandler(commandExecutionExceptionHandler);
            }
            cleanupThread.start();
        } catch (Throwable e) {
            Botify.registerListeners();
            throw e;
        }
    }

    private void doClean() {
        JDA jda = getContext().getJda();
        Session session = getContext().getSession();

        Set<String> activeGuildIds = jda.getGuilds().stream().map(ISnowflake::getId).collect(Collectors.toSet());
        CriteriaBuilder cb = session.getCriteriaBuilder();
        Set<Long> stalePlaylistIds = getStalePlaylistIds(cb, activeGuildIds, session);
        Set<Long> staleGuildSpecificationIds = getStaleGuildSpecificationIds(cb, activeGuildIds, session);
        StringBuilder messageBuilder = new StringBuilder();

        invoke(() -> {
            if (!stalePlaylistIds.isEmpty()) {
                deleteStalePlaylistItems(cb, stalePlaylistIds, session, Song.class);
                deleteStalePlaylistItems(cb, stalePlaylistIds, session, Video.class);
                deleteStalePlaylistItems(cb, stalePlaylistIds, session, UrlTrack.class);
                deleteStalePlaylists(cb, stalePlaylistIds, session);
                messageBuilder.append("Cleared ").append(stalePlaylistIds.size()).append(" playlists").append(System.lineSeparator());
            }

            if (!staleGuildSpecificationIds.isEmpty()) {
                Set<Long> staleAccessConfigurationsIds = getStaleAccessConfigurationsIds(cb, staleGuildSpecificationIds, session);
                if (!staleAccessConfigurationsIds.isEmpty()) {
                    deleteStaleGrantedRoles(cb, staleAccessConfigurationsIds, session);
                    deleteStaleAccessConfigurations(cb, staleAccessConfigurationsIds, session);
                    messageBuilder.append("Cleared ").append(staleAccessConfigurationsIds.size()).append(" access configurations")
                        .append(System.lineSeparator());
                }
                deleteStaleGuildSpecifications(cb, staleGuildSpecificationIds, session);
                messageBuilder.append("Cleared ").append(staleGuildSpecificationIds.size()).append(" guild specifications")
                    .append(System.lineSeparator());
            }

            int affectedPresets = deleteStalePresets(cb, activeGuildIds, session);
            if (affectedPresets > 0) {
                messageBuilder.append("Cleared ").append(affectedPresets).append(" presets");
            }
        });


        String message = messageBuilder.toString();
        if (!message.isBlank()) {
            sendSuccess(message);
        } else {
            sendSuccess("Nothing to clear");
        }
    }

    private void deleteStalePlaylists(CriteriaBuilder cb, Set<Long> playlistIds, Session session) {
        CriteriaDelete<Playlist> stalePlaylistsQuery = cb.createCriteriaDelete(Playlist.class);
        Root<Playlist> from = stalePlaylistsQuery.from(Playlist.class);
        stalePlaylistsQuery.where(cb.in(from.get("pk")).value(playlistIds));
        session.createQuery(stalePlaylistsQuery).executeUpdate();
    }

    private void deleteStalePlaylistItems(CriteriaBuilder cb, Set<Long> stalePlaylistIds, Session session, Class<? extends PlaylistItem> itemClass) {
        @SuppressWarnings("unchecked")
        Class<PlaylistItem> playlistItemClass = (Class<PlaylistItem>) itemClass;
        CriteriaDelete<PlaylistItem> stalePlaylistItemsQuery = cb.createCriteriaDelete(playlistItemClass);
        Root<PlaylistItem> playlistItemRoot = stalePlaylistItemsQuery.from(playlistItemClass);
        stalePlaylistItemsQuery.where(cb.in(playlistItemRoot.get("playlist").get("pk")).value(stalePlaylistIds));
        session.createQuery(stalePlaylistItemsQuery).executeUpdate();
    }

    private Set<Long> getStalePlaylistIds(CriteriaBuilder cb, Set<String> activeGuildIds, Session session) {
        CriteriaQuery<Long> stalePlaylistIdsQuery = cb.createQuery(Long.class);
        Root<Playlist> from = stalePlaylistIdsQuery.from(Playlist.class);
        stalePlaylistIdsQuery.select(from.get("pk"))
            .where(cb.not(cb.in(from.get("guildId")).value(activeGuildIds)));
        return session.createQuery(stalePlaylistIdsQuery).getResultStream().collect(Collectors.toSet());
    }

    private Set<Long> getStaleGuildSpecificationIds(CriteriaBuilder cb, Set<String> activeGuildIds, Session session) {
        CriteriaQuery<Long> staleGuildSpecificationsQuery = cb.createQuery(Long.class);
        Root<GuildSpecification> from = staleGuildSpecificationsQuery.from(GuildSpecification.class);
        staleGuildSpecificationsQuery.select(from.get("pk"))
            .where(cb.not(cb.in(from.get("guildId")).value(activeGuildIds)));
        return session.createQuery(staleGuildSpecificationsQuery).getResultStream().collect(Collectors.toSet());
    }

    private void deleteStaleGuildSpecifications(CriteriaBuilder cb, Set<Long> staleSpecificationIds, Session session) {
        CriteriaDelete<GuildSpecification> staleSpecificationsQuery = cb.createCriteriaDelete(GuildSpecification.class);
        Root<GuildSpecification> from = staleSpecificationsQuery.from(GuildSpecification.class);
        staleSpecificationsQuery.where(cb.in(from.get("pk")).value(staleSpecificationIds));
        session.createQuery(staleSpecificationsQuery).executeUpdate();
    }

    private Set<Long> getStaleAccessConfigurationsIds(CriteriaBuilder cb, Set<Long> staleSpecificationIds, Session session) {
        CriteriaQuery<Long> staleAccessConfigurationQuery = cb.createQuery(Long.class);
        Root<AccessConfiguration> from = staleAccessConfigurationQuery.from(AccessConfiguration.class);
        staleAccessConfigurationQuery.select(from.get("pk"))
            .where(cb.in(from.get("guildSpecification").get("pk")).value(staleSpecificationIds));
        return session.createQuery(staleAccessConfigurationQuery).getResultStream().collect(Collectors.toSet());
    }

    private void deleteStaleAccessConfigurations(CriteriaBuilder cb, Set<Long> staleAccessConfigurationIds, Session session) {
        CriteriaDelete<AccessConfiguration> staleAccessConfigurationsQuery = cb.createCriteriaDelete(AccessConfiguration.class);
        Root<AccessConfiguration> from = staleAccessConfigurationsQuery.from(AccessConfiguration.class);
        staleAccessConfigurationsQuery.where(cb.in(from.get("pk")).value(staleAccessConfigurationIds));
        session.createQuery(staleAccessConfigurationsQuery).executeUpdate();
    }

    private void deleteStaleGrantedRoles(CriteriaBuilder cb, Set<Long> staleAccessConfigurationIds, Session session) {
        CriteriaDelete<GrantedRole> staleGrantedRolesQuery = cb.createCriteriaDelete(GrantedRole.class);
        Root<GrantedRole> from = staleGrantedRolesQuery.from(GrantedRole.class);
        staleGrantedRolesQuery.where(cb.in(from.get("accessConfiguration").get("pk")).value(staleAccessConfigurationIds));
        session.createQuery(staleGrantedRolesQuery).executeUpdate();
    }

    private int deleteStalePresets(CriteriaBuilder cb, Set<String> activeGuildIds, Session session) {
        CriteriaDelete<Preset> stalePresetsQuery = cb.createCriteriaDelete(Preset.class);
        Root<Preset> from = stalePresetsQuery.from(Preset.class);
        stalePresetsQuery.where(cb.not(cb.in(from.get("guildId")).value(activeGuildIds)));
        return session.createQuery(stalePresetsQuery).executeUpdate();
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("silent")
            .setDescription("Disables alerting active guilds about the restart.");
        return argumentContribution;
    }

}
