package net.robinfriedli.botify.command.commands;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
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
        JDA jda = getContext().getJda();
        List<Guild> guilds = jda.getGuilds();
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
        double maxMemory = runtime.maxMemory() / Math.pow(1024, 2);
        double allocatedMemory = runtime.totalMemory() / Math.pow(1024, 2);
        double unallocatedMemory = maxMemory - allocatedMemory;
        double allocFreeMemory = runtime.freeMemory() / Math.pow(1024, 2);
        double usedMemory = allocatedMemory - allocFreeMemory;
        double totalFreeMemory = maxMemory - usedMemory;

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Guilds", String.valueOf(guildCount), true);
        embedBuilder.addField("Guilds active", String.valueOf(guildManager.getActiveGuilds(session).size()), true);
        embedBuilder.addField("Guilds playing now", String.valueOf(playingCount), true);
        embedBuilder.addField("Total commands entered", String.valueOf(commandCount), true);
        embedBuilder.addField("Saved playlists", String.valueOf(playlistCount), true);
        embedBuilder.addField("Saved tracks", String.valueOf(trackCount), true);
        embedBuilder.addField("Total tracks played", String.valueOf(playedCount), true);
        embedBuilder.addField("Memory (in MB)",
            "Total: " + round(maxMemory) + System.lineSeparator() +
                "Allocated: " + round(allocatedMemory) + System.lineSeparator() +
                "Unallocated: " + round(unallocatedMemory) + System.lineSeparator() +
                "Free allocated: " + round(allocFreeMemory) + System.lineSeparator() +
                "Currently used: " + round(usedMemory) + System.lineSeparator() +
                "Total free: " + round(totalFreeMemory)
            , false);

        sendWithLogo(embedBuilder);
    }

    private double round(double d) {
        BigDecimal bigDecimal = BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
        return bigDecimal.doubleValue();
    }

    @Override
    public void onSuccess() {
    }
}
