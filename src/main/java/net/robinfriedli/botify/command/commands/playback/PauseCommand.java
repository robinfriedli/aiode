package net.robinfriedli.botify.command.commands.playback;

import net.dv8tion.jda.api.entities.Guild;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.audio.AudioPlayback;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class PauseCommand extends AbstractCommand {

    public PauseCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresValue, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresValue, identifier, description, category);
    }

    @Override
    public void doRun() {
        Guild guild = getContext().getGuild();
        AudioPlayback playback = Botify.get().getAudioManager().getPlaybackForGuild(guild);
        playback.pause();
    }

    @Override
    public void onSuccess() {
    }

}
