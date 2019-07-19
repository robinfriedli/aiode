package net.robinfriedli.botify.command.commands.admin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.dv8tion.jda.core.EmbedBuilder;
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

        String output = getInputStreamString(process.getInputStream());
        String errors = getInputStreamString(process.getErrorStream());

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Output", output, false);
        embedBuilder.addField("Errors", errors, false);
        sendMessage(embedBuilder);
    }

    @Override
    public void onSuccess() {
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
