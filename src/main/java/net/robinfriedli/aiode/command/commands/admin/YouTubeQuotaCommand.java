package net.robinfriedli.aiode.command.commands.admin;

import jakarta.persistence.LockModeType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.youtube.YouTubeService;
import net.robinfriedli.aiode.command.AbstractAdminCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;

public class YouTubeQuotaCommand extends AbstractAdminCommand {

    public YouTubeQuotaCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void runAdmin() {
        YouTubeService youTubeService = Aiode.get().getAudioManager().getYouTubeService();
        int atomic = youTubeService.getAtomicQuotaUsage();
        int persistent = YouTubeService.getCurrentQuotaUsage(getContext().getSession(), LockModeType.NONE).getQuota();
        int limit = Aiode.get().getSpringPropertiesConfig().requireApplicationProperty(Integer.class, "aiode.preferences.youtube_api_daily_quota");

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
