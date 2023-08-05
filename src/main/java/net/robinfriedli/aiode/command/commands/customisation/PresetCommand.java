package net.robinfriedli.aiode.command.commands.customisation;

import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.parser.CommandParser;
import net.robinfriedli.aiode.discord.property.properties.ArgumentPrefixProperty;
import net.robinfriedli.aiode.entities.Preset;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import net.robinfriedli.aiode.util.EmbedTable;
import net.robinfriedli.aiode.util.SearchEngine;
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

            invoke(() -> session.remove(preset));
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
