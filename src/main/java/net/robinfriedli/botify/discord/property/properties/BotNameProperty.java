package net.robinfriedli.botify.discord.property.properties;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.commands.customisation.RenameCommand;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.AbstractGuildProperty;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.exceptions.InvalidPropertyValueException;
import org.hibernate.Session;

/**
 * Property that customised the bot name that may be used as command prefix.
 */
public class BotNameProperty extends AbstractGuildProperty {

    public static final String DEFAULT_FALLBACK = "$botify";

    public BotNameProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    public static String getForContext(GuildContext guildContext, Session session) {
        GuildSpecification specification = guildContext.getSpecification(session);
        GuildPropertyManager guildPropertyManager = Botify.get().getGuildPropertyManager();

        return guildPropertyManager
            .getPropertyValueOptional("botName", String.class, specification)
            .orElse(DEFAULT_FALLBACK);
    }

    @Override
    public void validate(Object state) {
        String input = (String) state;
        if (input.length() < 1 || input.length() > 32) {
            throw new InvalidPropertyValueException("Length should be 1 - 32 characters");
        }
    }

    @Override
    public Object process(String input) {
        return input;
    }

    @Override
    public void setValue(String value, GuildSpecification guildSpecification) {
        Botify botify = Botify.get();
        Guild guild = guildSpecification.getGuild(botify.getShardManager());
        if (guild != null) {
            RenameCommand.RENAME_SYNC.execute(guild.getIdLong(), () -> {
                try {
                    guild.getSelfMember().modifyNickname(value).queue();
                } catch (InsufficientPermissionException e) {
                    ExecutionContext executionContext = ExecutionContext.Current.get();
                    if (executionContext != null) {
                        MessageService messageService = botify.getMessageService();
                        TextChannel channel = executionContext.getChannel();
                        messageService.sendError("I do not have permission to change my nickname, but you can still call me " + value, channel);
                    }
                }
                guildSpecification.setBotName(value);
            });
        } else {
            guildSpecification.setBotName(value);
        }
    }

    @Override
    public Object extractPersistedValue(GuildSpecification guildSpecification) {
        return guildSpecification.getBotName();
    }
}
