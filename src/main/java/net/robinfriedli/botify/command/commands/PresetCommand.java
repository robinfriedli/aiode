package net.robinfriedli.botify.command.commands;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.dv8tion.jda.core.EmbedBuilder;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.ArgumentContribution;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.Preset;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.SearchEngine;
import net.robinfriedli.botify.util.Table2;
import org.hibernate.Session;

public class PresetCommand extends AbstractCommand {

    public PresetCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description, Category.CUSTOMISATION);
    }

    @Override
    public void doRun() {
        Session session = getContext().getSession();
        String guildId = getContext().getGuild().getId();
        if (argumentSet("delete")) {
            Preset preset = SearchEngine.searchPreset(session, getCommandBody(), guildId);

            if (preset == null) {
                throw new InvalidCommandException("No preset found for " + getCommandBody());
            }

            invoke(() -> session.delete(preset));
        } else if (getCommandBody().isBlank()) {
            List<Preset> presets = session.createQuery("from " + Preset.class.getName() + " where guild_id = '" + guildId + "'", Preset.class).getResultList();
            EmbedBuilder embedBuilder = new EmbedBuilder();
            if (presets.isEmpty()) {
                embedBuilder.setDescription("No presets saved");
            } else {
                Table2 table = new Table2(embedBuilder);
                table.addColumn("Name", presets, Preset::getName);
                table.addColumn("Preset", presets, Preset::getPreset);
                table.build();
            }
            sendMessage(embedBuilder);
        } else {
            Pair<String, String> pair = splitInlineArgument("as");
            String presetString = pair.getLeft();
            String name = pair.getRight();
            Preset existingPreset = SearchEngine.searchPreset(getContext().getSession(), name, getContext().getGuild().getId());
            if (existingPreset != null) {
                throw new InvalidCommandException("Preset " + name + " already exists");
            }

            Preset preset = new Preset(name, presetString, getContext().getGuild(), getContext().getUser());
            String testString = presetString.contains("%s") ? name + " test" : name;
            AbstractCommand abstractCommand = preset.instantiateCommand(getManager(), getContext(), testString);
            abstractCommand.verify();
            invoke(() -> session.persist(preset));
        }
    }

    @Override
    public void onSuccess() {
        // success notification sent by interceptor
    }

    @Override
    public ArgumentContribution setupArguments() {
        ArgumentContribution argumentContribution = new ArgumentContribution(this);
        argumentContribution.map("delete").setRequiresInput(true)
            .setDescription("Delete an existing preset by its name.");
        return argumentContribution;
    }
}
