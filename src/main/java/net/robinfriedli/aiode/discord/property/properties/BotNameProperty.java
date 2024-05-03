package net.robinfriedli.aiode.discord.property.properties;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.command.commands.customisation.RenameCommand;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.discord.GuildContext;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.discord.property.AbstractGuildProperty;
import net.robinfriedli.aiode.discord.property.GuildPropertyManager;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;
import net.robinfriedli.aiode.exceptions.InvalidPropertyValueException;
import org.hibernate.Session;

/**
 * Property that customised the bot name that may be used as command prefix.
 */
public class BotNameProperty extends AbstractGuildProperty {

    public static final String DEFAULT_FALLBACK = "$aiode";

    public BotNameProperty(GuildPropertyContribution contribution) {
        super(contribution);
    }

    public static String getForContext(GuildContext guildContext, Session session) {
        GuildSpecification specification = guildContext.getSpecification(session);
        GuildPropertyManager guildPropertyManager = Aiode.get().getGuildPropertyManager();

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
        Aiode aiode = Aiode.get();
        Guild guild = guildSpecification.getGuild(aiode.getShardManager());
        if (guild != null) {
            RenameCommand.RENAME_SYNC.run(guild.getIdLong(), () -> {
                try {
                    guild.getSelfMember().modifyNickname(value).queue();
                } catch (InsufficientPermissionException e) {
                    ExecutionContext executionContext = ExecutionContext.Current.get();
                    if (executionContext != null) {
                        MessageService messageService = aiode.getMessageService();
                        MessageChannel channel = executionContext.getChannel();
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
