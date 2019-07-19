package net.robinfriedli.botify.command.commands.admin;

import net.dv8tion.jda.core.EmbedBuilder;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class QuitCommand extends AbstractAdminCommand {

    public QuitCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description);
    }

    @Override
    public void runAdmin() {
        StringBuilder confirmationMessageBuilder = new StringBuilder("Do you really want to stop the bot?");
        if (!getCommandBody().isBlank()) {
            confirmationMessageBuilder.append(" Reason: '").append(getCommandBody()).append("'");
        }
        askConfirmation(confirmationMessageBuilder.toString());
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public void withUserResponse(Object chosenOption) {
        if ((boolean) chosenOption) {
            doQuit();
        }
    }

    private void doQuit() {
        Botify.shutdownListeners();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Scheduled shutdown");
        embedBuilder.setDescription("The bot is scheduled to shut down after completing queued actions. No commands will be accepted until then.");
        if (!getCommandBody().isBlank()) {
            embedBuilder.addField("Reason", getCommandBody(), false);
        }
        sendToActiveGuilds(embedBuilder.build());

        // runs in separate thread to avoid deadlock when waiting for commands to finish
        Thread shutdownThread = new Thread(() -> Botify.shutdown(60000));
        shutdownThread.setName("Shutdown thread");
        shutdownThread.start();
    }

}
