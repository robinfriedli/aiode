package net.robinfriedli.aiode.command.commands.playback;

import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioManager;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;

public class StopCommand extends AbstractCommand {

    public StopCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        AudioManager audioManager = Aiode.get().getAudioManager();
        AudioPlayback playback = audioManager.getPlaybackForGuild(guild);
        getContext().getGuildContext().getReplaceableTrackLoadingExecutor().abort();
        playback.stop();
        playback.getAudioQueue().clear();
    }

    @Override
    public void onSuccess() {
    }
}
