package net.robinfriedli.aiode.command.commands.playback;

import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.AudioPlayback;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;

public class PauseCommand extends AbstractCommand {

    public PauseCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresValue, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresValue, identifier, description, category);
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        AudioPlayback playback = Aiode.get().getAudioManager().getPlaybackForGuild(guild);
        playback.pause();
    }

    @Override
    public void onSuccess() {
    }

}
