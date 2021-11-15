package net.robinfriedli.aiode.command.commands.admin;

import java.io.InputStream;
import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.command.AbstractAdminCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.entities.xml.EmbedDocumentContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.util.Util;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class LoadDocumentCommand extends AbstractAdminCommand {

    public LoadDocumentCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void runAdmin() {
        JxpBackend jxpBackend = Aiode.get().getJxpBackend();
        EmbedBuilder embedBuilder;
        InputStream embedDocumentResource = getClass().getResourceAsStream("/xml-contributions/embedDocuments.xml");
        try (Context context = jxpBackend.createLazyContext(embedDocumentResource)) {
            if (getCommandInput().isBlank()) {
                List<EmbedDocumentContribution> documents = context.getInstancesOf(EmbedDocumentContribution.class);
                embedBuilder = new EmbedBuilder();
                Util.appendEmbedList(embedBuilder, documents, EmbedDocumentContribution::getName, "Documents");
            } else {
                EmbedDocumentContribution document = context
                    .query(attribute("name").fuzzyIs(getCommandInput()), EmbedDocumentContribution.class)
                    .getOnlyResult();

                if (document == null) {
                    throw new InvalidCommandException(String.format("No embed document found for '%s'", getCommandInput()));
                }

                embedBuilder = document.buildEmbed();
            }
        }

        sendMessage(embedBuilder);
    }

    @Override
    public void onSuccess() {
    }
}
