package net.robinfriedli.botify.command.commands.customisation;

import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.parser.CommandParser;
import net.robinfriedli.botify.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.EmbedTable;
import net.robinfriedli.botify.util.SearchEngine;
import org.hibernate.Session;

public class PresetCommand extends AbstractCommand {

    public PresetCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        if (argumentSet("delete")) {
            Preset preset = SearchEngine.searchPreset(session, getCommandInput());

            if (preset == null) {
                throw new InvalidCommandException(String.format("No preset found for '%s'", getCommandInput()));
            }

            invoke(() -> session.delete(preset));
        } else if (getCommandInput().isBlank()) {
            List<Preset> presets = getQueryBuilderFactory().find(Preset.class).build(session).getResultList();
            EmbedBuilder embedBuilder = new EmbedBuilder();
            if (presets.isEmpty()) {
                embedBuilder.setDescription("No presets saved");
            } else {
                EmbedTable table = new EmbedTable(embedBuilder);
                table.addColumn("Name", presets, Preset::getName);
                table.addColumn("Preset", presets, Preset::getPreset);
                table.build();
            }
            sendMessage(embedBuilder);
        } else {
            String presetString = getCommandInput();
            String name = getArgumentValue("as");
            Preset existingPreset = SearchEngine.searchPreset(getContext().getSession(), name);
            if (existingPreset != null) {
                throw new InvalidCommandException("Preset " + name + " already exists");
            }

            Preset preset = new Preset(name, presetString, getContext().getGuild(), getContext().getUser());
            String testString = presetString.contains("%s") ? name + " test" : name;
            AbstractCommand abstractCommand = preset.instantiateCommand(getManager(), getContext(), testString);
            CommandParser commandParser = new CommandParser(abstractCommand, ArgumentPrefixProperty.getForCurrentContext());
            commandParser.parse();
            abstractCommand.verify();
            invoke(() -> session.persist(preset));
        }
    }

    @Override
    public void onSuccess() {
        // success notification sent by interceptor
    }

}
