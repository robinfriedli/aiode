package net.robinfriedli.botify.command.commands.customisation;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.util.EmbedTable;

import static net.robinfriedli.jxp.queries.Conditions.*;

public class PropertyCommand extends AbstractCommand {

    public PropertyCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        if (getCommandInput().isBlank()) {
            listProperties();
        } else {
            if (argumentSet("toggle")) {
                toggleProperty();
            } else if (argumentSet("describe")) {
                describeProperty();
            } else {
                setProperty();
            }
        }
    }

    private void setProperty() {
        AbstractGuildProperty property = Botify.get().getGuildPropertyManager().getPropertyByName(getCommandInput());
        if (property != null) {
            property.set(getArgumentValue("set"));
        } else {
            throw new InvalidCommandException("No such property '" + getCommandInput() + "'");
        }
    }

    private void toggleProperty() {
        AbstractGuildProperty property = Botify.get().getGuildPropertyManager().getPropertyByName(getCommandInput());
        if (property != null) {
            Object value = property.get();
            if (value instanceof Boolean) {
                boolean newBoolValue = !((boolean) value);
                property.set(String.valueOf(newBoolValue));
            } else {
                throw new InvalidCommandException("Value of property '" + property.getName() + "' is not a boolean");
            }
        } else {
            throw new InvalidCommandException("No such property '" + getCommandInput() + "'");
        }
    }

    private void listProperties() {
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
        List<AbstractGuildProperty> properties = guildPropertyManager.getProperties();
        GuildSpecification specification = getContext().getGuildContext().getSpecification();

        EmbedBuilder embedBuilder = new EmbedBuilder();
        EmbedTable table = new EmbedTable(embedBuilder);
        table.addColumn("Name", properties, AbstractGuildProperty::getName);
        table.addColumn("Default Value", properties, AbstractGuildProperty::getDefaultValue);
        table.addColumn("Set Value", properties, property -> property.display(specification));
        table.build();
        sendMessage(embedBuilder);
    }

    private void describeProperty() {
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();
        String propertyName = getCommandInput();
        AbstractGuildProperty property = guildPropertyManager.getPropertyByName(propertyName);

        if (property != null) {
            GuildPropertyContribution contribution = property.getContribution();

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Property " + contribution.getName());
            embedBuilder.setDescription(contribution.getDescription());

            long acceptedValuesCount = contribution.query(tagName("acceptedValue")).count();
            if (acceptedValuesCount > 0) {
                embedBuilder.addField("Accepted values", contribution.getAcceptedValuesString(), false);
            }

            sendMessage(embedBuilder);
        } else {
            throw new InvalidCommandException(String.format("No such property '%s'", propertyName));
        }
    }

    @Override
    public void onSuccess() {
        // notification sent by GuildPropertyInterceptor
    }

    @Override
    public void withUserResponse(Object chosenOption) {
        if (chosenOption instanceof Collection) {
            throw new InvalidCommandException("Required single selection");
        }

        if (chosenOption instanceof TextChannel) {
            AbstractGuildProperty defaultTextChannelProperty = Botify.get().getGuildPropertyManager().getProperty("defaultTextChannelId");
            if (defaultTextChannelProperty != null) {
                defaultTextChannelProperty.set(((TextChannel) chosenOption).getId());
            }
        }
    }
}
