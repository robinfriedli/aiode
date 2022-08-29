package net.robinfriedli.aiode.command.commands.admin;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.robinfriedli.aiode.command.AbstractAdminCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.stringlist.StringList;

public class UpdateCommand extends AbstractAdminCommand {

    private final List<OutputAttachment> attachments = Lists.newArrayList();

    public UpdateCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void runAdmin() throws Exception {
        ProcessBuilder updateProcess = new ProcessBuilder("bash", "bash/update.sh");
        Process process = updateProcess.start();
        process.waitFor();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        attachOutput(embedBuilder, "Success", process.getInputStream());
        attachOutput(embedBuilder, "Error", process.getErrorStream());
        embedBuilder.setColor(ColorSchemeProperty.getColor());
        MessageEmbed messageEmbed = embedBuilder.build();

        getMessageService().executeMessageAction(getContext().getChannel(), channel -> {
            MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder().addEmbeds(messageEmbed);

            for (OutputAttachment attachment : attachments) {
                // #addFile simply returns the current MessageAction which is returned below
                messageCreateBuilder = messageCreateBuilder.addFiles(FileUpload.fromData(attachment.getInputStream(), attachment.getName()));
            }

            return channel.sendMessage(messageCreateBuilder.build());
        }, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES);
    }

    @Override
    public void onSuccess() {
    }

    private void attachOutput(EmbedBuilder embedBuilder, String title, InputStream inputStream) throws IOException {
        String inputStreamString = getInputStreamString(inputStream);

        if (inputStreamString.isBlank()) {
            return;
        }

        if (inputStreamString.length() < 1024) {
            embedBuilder.addField(title, "```\n" + inputStreamString + "\n```", false);
        } else {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(inputStreamString.getBytes());
            embedBuilder.addField(title, "Output too long, sending as attachment", false);
            attachments.add(new OutputAttachment(byteArrayInputStream, title + ".txt"));
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

    private static class OutputAttachment {

        private final ByteArrayInputStream inputStream;
        private final String name;

        private OutputAttachment(ByteArrayInputStream inputStream, String name) {
            this.inputStream = inputStream;
            this.name = name;
        }

        public ByteArrayInputStream getInputStream() {
            return inputStream;
        }

        public String getName() {
            return name;
        }
    }

}
