package net.robinfriedli.botify.command.commands.admin;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class RebootCommand extends AbstractAdminCommand {

    public RebootCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description);
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
            if (!argumentSet("silent")) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("Scheduled restart");
                embedBuilder.setDescription("The bot is scheduled to restart after completing pending actions. No commands will be accepted until then.");
                if (!getCommandInput().isBlank()) {
                    embedBuilder.addField("Reason", getCommandInput(), false);
                }
                sendToActiveGuilds(embedBuilder.build());
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
            Thread shutdownThread = new Thread(() -> Botify.shutdown(getArgumentValue("await", Integer.class, 60) * 1000));
            shutdownThread.setName("Shutdown thread");
            shutdownThread.start();
        } catch (Throwable e) {
            Botify.registerListeners();
            throw e;
        }
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("silent")
            .setDescription("Disables alerting active guilds about the restart.");
        argumentContribution.map("await")
            .verifyValue(Integer.class, value -> value <= 600, "Maximum value is 600")
            .verifyValue(Integer.class, value -> value >= 0, "Value needs to be 0 or greater")
            .setDescription("The maximum amount of seconds to wait for pending actions to complete.");
        return argumentContribution;
    }
}
