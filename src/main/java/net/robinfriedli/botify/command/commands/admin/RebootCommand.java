package net.robinfriedli.botify.command.commands.admin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class RebootCommand extends AbstractAdminCommand {

    public RebootCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void runAdmin() {
        StringBuilder confirmationMessageBuilder = new StringBuilder("Do you really want to restart the bot?");
        if (!getCommandInput().isBlank()) {
            confirmationMessageBuilder.append(" Reason: '").append(getCommandInput()).append("'");
        }
        askConfirmation(confirmationMessageBuilder.toString());
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public void withUserResponse(Object chosenOption) {
        if (chosenOption instanceof Collection) {
            throw new InvalidCommandException("Expected a single selection");
        }

        if ((boolean) chosenOption) {
            doRestart();
        }
    }

    private void doRestart() {
        Botify.shutdownListeners();

        try {
            List<CompletableFuture<Message>> messagesToAwait;
            if (!argumentSet("silent")) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("Scheduled restart");
                embedBuilder.setDescription("The bot is scheduled to restart after completing pending actions. No commands will be accepted until then.");
                if (!getCommandInput().isBlank()) {
                    embedBuilder.addField("Reason", getCommandInput(), false);
                }
                messagesToAwait = sendToActiveGuilds(embedBuilder.build());
            } else {
                messagesToAwait = null;
            }

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
            Thread shutdownThread = new Thread(() -> Botify.shutdown(
                getArgumentValue("await", Integer.class, 60) * 1000,
                messagesToAwait
            ));
            shutdownThread.setName("Shutdown thread");
            shutdownThread.start();
        } catch (Throwable e) {
            Botify.registerListeners();
            throw e;
        }
    }

}
