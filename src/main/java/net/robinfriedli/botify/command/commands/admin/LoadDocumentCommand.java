package net.robinfriedli.botify.command.commands.admin;

import java.util.List;

import net.dv8tion.jda.core.EmbedBuilder;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.entities.xml.EmbedDocumentContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.PropertiesLoadingService;
import net.robinfriedli.botify.util.Util;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class LoadDocumentCommand extends AbstractAdminCommand {

    public LoadDocumentCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description);
    }

    @Override
    public void runAdmin() {
        JxpBackend jxpBackend = Botify.get().getJxpBackend();
        EmbedBuilder embedBuilder;
        try (Context context = jxpBackend.createLazyContext(PropertiesLoadingService.requireProperty("EMBED_DOCUMENTS_PATH"))) {
            if (getCommandBody().isBlank()) {
                List<EmbedDocumentContribution> documents = context.getInstancesOf(EmbedDocumentContribution.class);
                embedBuilder = new EmbedBuilder();
                Util.appendEmbedList(embedBuilder, documents, EmbedDocumentContribution::getName, "Documents");
            } else {
                EmbedDocumentContribution document = context
                    .query(attribute("name").fuzzyIs(getCommandBody()), EmbedDocumentContribution.class)
                    .getOnlyResult();

                if (document == null) {
                    throw new InvalidCommandException("No embed document found for " + getCommandBody());
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
