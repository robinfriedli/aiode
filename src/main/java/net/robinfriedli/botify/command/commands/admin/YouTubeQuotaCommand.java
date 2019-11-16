package net.robinfriedli.botify.command.commands.admin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.youtube.YouTubeService;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.util.PropertiesLoadingService;

public class YouTubeQuotaCommand extends AbstractAdminCommand {

    public YouTubeQuotaCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description);
    }

    @Override
    public void runAdmin() {
        YouTubeService youTubeService = Botify.get().getAudioManager().getYouTubeService();
        int atomic = youTubeService.getAtomicQuotaUsage();
        int persistent = YouTubeService.getCurrentQuotaUsage(getContext().getSession()).getQuota();
        int limit = PropertiesLoadingService.requireProperty(Integer.class, "YOUTUBE_API_DAILY_QUOTA");

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("YouTube API quota usage");
        embedBuilder.setDescription("Displays the current approximate usage of the daily YouTube API quota");
        embedBuilder.addField("Current atomic value", String.valueOf(atomic), true);
        embedBuilder.addField("Current persistent value", String.valueOf(persistent), true);
        embedBuilder.addField("Daily limit", String.valueOf(limit), true);

        sendMessage(embedBuilder);
    }

    @Override
    public void onSuccess() {
    }
}
