package net.robinfriedli.botify.command.commands.admin;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.stringlist.StringList;

public class UpdateCommand extends AbstractAdminCommand {

    public UpdateCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description);
    }

    @Override
    public void runAdmin() throws Exception {
        ProcessBuilder updateProcess = new ProcessBuilder("bash", "resources/bash/update.sh");
        Process process = updateProcess.start();
        process.waitFor();

        sendOutput(process);
    }

    @Override
    public void onSuccess() {
    }

    private void sendOutput(Process process) throws IOException {
        String inputStreamString = getInputStreamString(process.getInputStream());
        if (inputStreamString.length() < 2000) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Output");
            embedBuilder.setDescription(inputStreamString);
            sendMessage(embedBuilder);
        } else {
            MessageChannel channel = getContext().getChannel();
            Message message = new MessageBuilder().append("Output too long, attaching as file").build();
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(inputStreamString.getBytes());
            getMessageService().executeMessageAction(channel, c -> {
                MessageAction messageAction = c.sendMessage(message);
                return messageAction.addFile(byteArrayInputStream, "output.txt");
            });
        }
    }

    private String getInputStreamString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringList lineList = StringList.create();
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            lineList.add(s);
        }

        return lineList.toSeparatedString(System.lineSeparator());
    }

}
