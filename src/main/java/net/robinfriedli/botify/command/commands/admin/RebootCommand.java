package net.robinfriedli.botify.command.commands.admin;

import java.io.IOException;
import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.core.EmbedBuilder;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class RebootCommand extends AbstractAdminCommand {

    public RebootCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description);
    }

    @Override
    public void runAdmin() {
        StringBuilder confirmationMessageBuilder = new StringBuilder("Do you really want to restart the bot?");
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
            doRestart();
        }
    }

    private void doRestart() {
        Botify.shutdownListeners();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Scheduled restart");
        embedBuilder.setDescription("The bot is scheduled to restart after completing pending actions. No commands will be accepted until then.");
        if (!getCommandBody().isBlank()) {
            embedBuilder.addField("Reason", getCommandBody(), false);
        }
        sendToActiveGuilds(embedBuilder.build());

        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new Thread(() -> {
            Logger logger = LoggerFactory.getLogger(getClass());
            logger.info("Restarting bot");

            PrintStream out = System.out;
            try {
                Botify.launch();
            } catch (IOException e) {
                out.println("new process could not be started: " + e.getMessage());
            }
        }));

        // runs in separate thread to avoid deadlock when waiting for commands to finish
        Thread shutdownThread = new Thread(() -> Botify.shutdown(60000));
        shutdownThread.setName("Shutdown thread");
        shutdownThread.start();
    }

}
