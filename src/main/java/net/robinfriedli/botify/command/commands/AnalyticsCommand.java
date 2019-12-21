package net.robinfriedli.botify.command.commands;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioManager;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.CommandHistory;
import net.robinfriedli.botify.entities.PlaybackHistory;
import net.robinfriedli.botify.entities.Playlist;
import net.robinfriedli.botify.entities.Song;
import net.robinfriedli.botify.entities.UrlTrack;
import net.robinfriedli.botify.entities.Video;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import org.hibernate.Session;

public class AnalyticsCommand extends AbstractCommand {

    public AnalyticsCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.GENERAL);
    }

    @Override
    public void doRun() {
        ShardManager shardManager = Botify.get().getShardManager();
        List<Guild> guilds = shardManager.getGuilds();
        Botify botify = Botify.get();
        AudioManager audioManager = botify.getAudioManager();
        GuildManager guildManager = botify.getGuildManager();
        Session session = getContext().getSession();
        Runtime runtime = Runtime.getRuntime();

        int guildCount = guilds.size();
        long playingCount = guilds.stream().map(audioManager::getPlaybackForGuild).filter(AudioPlayback::isPlaying).count();
        long commandCount = session.createQuery("select count(*) from " + CommandHistory.class.getName(), Long.class).uniqueResult();
        long playlistCount = session.createQuery("select count(*) from " + Playlist.class.getName(), Long.class).uniqueResult();
        long trackCount = session.createQuery("select count(*) from " + Song.class.getName(), Long.class).uniqueResult()
            + session.createQuery("select count(*) from " + Video.class.getName(), Long.class).uniqueResult()
            + session.createQuery("select count(*) from " + UrlTrack.class.getName(), Long.class).uniqueResult();
        long playedCount = session.createQuery("select count(*) from " + PlaybackHistory.class.getName(), Long.class).uniqueResult();
        // convert to MB by right shifting by 20 bytes (same as dividing by 2^20)
        long maxMemory = runtime.maxMemory() >> 20;
        long allocatedMemory = runtime.totalMemory() >> 20;
        long unallocatedMemory = maxMemory - allocatedMemory;
        long allocFreeMemory = runtime.freeMemory() >> 20;
        long usedMemory = allocatedMemory - allocFreeMemory;
        long totalFreeMemory = maxMemory - usedMemory;

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadMXBean.getThreadCount();
        int daemonThreadCount = threadMXBean.getDaemonThreadCount();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Guilds", String.valueOf(guildCount), true);
        embedBuilder.addField("Guilds active", String.valueOf(guildManager.getActiveGuilds(session).size()), true);
        embedBuilder.addField("Guilds playing now", String.valueOf(playingCount), true);
        embedBuilder.addField("Total commands entered", String.valueOf(commandCount), true);
        embedBuilder.addField("Saved playlists", String.valueOf(playlistCount), true);
        embedBuilder.addField("Saved tracks", String.valueOf(trackCount), true);
        embedBuilder.addField("Total tracks played", String.valueOf(playedCount), true);
        embedBuilder.addField("Thread count", String.format("%s (%s daemons)", threadCount, daemonThreadCount), true);
        embedBuilder.addField("Memory (in MB)",
            "Total: " + maxMemory + System.lineSeparator() +
                "Allocated: " + allocatedMemory + System.lineSeparator() +
                "Unallocated: " + unallocatedMemory + System.lineSeparator() +
                "Free allocated: " + allocFreeMemory + System.lineSeparator() +
                "Currently used: " + usedMemory + System.lineSeparator() +
                "Total free: " + totalFreeMemory
            , false);

        sendWithLogo(embedBuilder);
    }

    @Override
    public void onSuccess() {
    }
}
