package net.robinfriedli.botify.command.commands.admin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.stringlist.StringList;
import net.robinfriedli.stringlist.StringListImpl;

public class UpdateCommand extends AbstractAdminCommand {

    public UpdateCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description);
    }

    @Override
    public void runAdmin() throws Exception {
        ProcessBuilder updateProcess = new ProcessBuilder("bash", "resources/bash/update.sh");
        Process process = updateProcess.start();
        process.waitFor();

        sendOutput(process.getInputStream());
    }

    @Override
    public void onSuccess() {
    }

    private void sendOutput(InputStream stream) throws IOException {
        String inputStreamString = getInputStreamString(stream);
        if (inputStreamString.length() < 2000) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Output");
            embedBuilder.setDescription(inputStreamString);
            sendMessage(embedBuilder);
        } else {
            MessageChannel channel = getContext().getChannel();
            CompletableFuture<Message> futureMessage = sendMessage("Output too long, attaching as file");
            futureMessage.thenAccept(message -> channel.sendFile(stream, "output.txt", message).queue());
        }
    }

    private String getInputStreamString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringList lineList = StringListImpl.create();
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            lineList.add(s);
        }

        return lineList.toSeparatedString(System.lineSeparator());
    }

}
