package net.robinfriedli.aiode.command.commands.admin;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.command.AbstractAdminCommand;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.concurrent.CommandExecutionQueueManager;
import net.robinfriedli.aiode.concurrent.ThreadContext;
import net.robinfriedli.aiode.discord.GuildManager;
import net.robinfriedli.aiode.entities.AccessConfiguration;
import net.robinfriedli.aiode.entities.GrantedRole;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.Playlist;
import net.robinfriedli.aiode.entities.PlaylistItem;
import net.robinfriedli.aiode.entities.Preset;
import net.robinfriedli.aiode.entities.Song;
import net.robinfriedli.aiode.entities.StoredScript;
import net.robinfriedli.aiode.entities.UrlTrack;
import net.robinfriedli.aiode.entities.Video;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.persist.StaticSessionProvider;
import org.hibernate.Session;

public class CleanDbCommand extends AbstractAdminCommand {

    public CleanDbCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void runAdmin() {
        Aiode.shutdownListeners();
        try {
            if (!argumentSet("silent")) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("Scheduled cleanup");
                embedBuilder.setDescription("Aiode is suspending for a few seconds to clean the database.");
                sendToActiveGuilds(embedBuilder.build());
            }

            CommandExecutionQueueManager executionQueueManager = Aiode.get().getExecutionQueueManager();
            Command command = this;
            Thread cleanupThread = new Thread(() -> {
                ThreadContext.Current.install(command);
                try {
                    try {
                        executionQueueManager.joinAll(60000);
                    } catch (InterruptedException e) {
                        return;
                    }

                    StaticSessionProvider.consumeSession(this::doClean);
                } finally {
                    Aiode.registerListeners();
                }
            });
            cleanupThread.setName("database-cleanup-thread-" + command.getContext().toString());
            Thread.UncaughtExceptionHandler commandExecutionExceptionHandler = Thread.currentThread().getUncaughtExceptionHandler();
            if (commandExecutionExceptionHandler != null) {
                cleanupThread.setUncaughtExceptionHandler(commandExecutionExceptionHandler);
            }
            cleanupThread.start();
        } catch (Throwable e) {
            Aiode.registerListeners();
            throw e;
        }
    }

    private void doClean(Session session) {
        ShardManager shardManager = Aiode.get().getShardManager();

        Set<String> activeGuildIds = shardManager.getGuilds().stream().map(ISnowflake::getId).collect(Collectors.toSet());
        GuildManager guildManager = Aiode.get().getGuildManager();
        activeGuildIds.addAll(guildManager.getGuildContexts().stream().map(g -> g.getGuild().getId()).collect(Collectors.toSet()));
        CriteriaBuilder cb = session.getCriteriaBuilder();
        Set<Long> stalePlaylistIds = getStalePlaylistIds(cb, activeGuildIds, session);
        Set<Long> staleGuildSpecificationIds = getStaleGuildSpecificationIds(cb, activeGuildIds, session);
        StringBuilder messageBuilder = new StringBuilder();

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
            messageBuilder.append("Cleared ").append(affectedPresets).append(" presets").append(System.lineSeparator());
        }

        int affectedScripts = deleteStaleScripts(cb, activeGuildIds, session);
        if (affectedScripts > 0) {
            messageBuilder.append("Cleared ").append(affectedScripts).append(" scripts");
        }


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
        session.createMutationQuery(stalePlaylistsQuery).executeUpdate();
    }

    private void deleteStalePlaylistItems(CriteriaBuilder cb, Set<Long> stalePlaylistIds, Session session, Class<? extends PlaylistItem> itemClass) {
        @SuppressWarnings("unchecked")
        Class<PlaylistItem> playlistItemClass = (Class<PlaylistItem>) itemClass;
        CriteriaDelete<PlaylistItem> stalePlaylistItemsQuery = cb.createCriteriaDelete(playlistItemClass);
        Root<PlaylistItem> playlistItemRoot = stalePlaylistItemsQuery.from(playlistItemClass);
        stalePlaylistItemsQuery.where(cb.in(playlistItemRoot.get("playlist").get("pk")).value(stalePlaylistIds));
        session.createMutationQuery(stalePlaylistItemsQuery).executeUpdate();
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

    private int deleteStaleScripts(CriteriaBuilder cb, Set<String> activeGuildIds, Session session) {
        Set<Long> guildIdsLong = activeGuildIds.stream().map(Long::parseLong).collect(Collectors.toSet());
        CriteriaDelete<StoredScript> staleScriptsQuery = cb.createCriteriaDelete(StoredScript.class);
        Root<StoredScript> from = staleScriptsQuery.from(StoredScript.class);
        staleScriptsQuery.where(cb.not(cb.in(from.get("guildId")).value(guildIdsLong)));
        return session.createQuery(staleScriptsQuery).executeUpdate();
    }

    @Override
    public void onSuccess() {
    }

}
