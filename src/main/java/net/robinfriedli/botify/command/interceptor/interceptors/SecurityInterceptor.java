package net.robinfriedli.botify.command.interceptor.interceptors;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import org.hibernate.Session;

/**
 * Interceptor that checks whether a member is allowed to use the current command
 */
public class SecurityInterceptor extends AbstractChainableCommandInterceptor {

    private final GuildManager guildManager;
    private final GuildPropertyManager guildPropertyManager;
    private final SecurityManager securityManager;
    private final SpringPropertiesConfig springPropertiesConfig;

    public SecurityInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next, GuildManager guildManager, GuildPropertyManager guildPropertyManager, SecurityManager securityManager, SpringPropertiesConfig springPropertiesConfig) {
        super(contribution, next);
        this.guildManager = guildManager;
        this.guildPropertyManager = guildPropertyManager;
        this.securityManager = securityManager;
        this.springPropertiesConfig = springPropertiesConfig;
    }

    @Override
    public void performChained(Command command) {
        CommandContext context = command.getContext();
        User user = context.getUser();
        Guild guild = context.getGuild();

        if (command instanceof AbstractCommand && ((AbstractCommand) command).getCategory() == AbstractCommand.Category.SCRIPTING) {
            if (!springPropertiesConfig.requireApplicationProperty(Boolean.class, "botify.preferences.enableScripting")) {
                throw new InvalidCommandException("The bot hoster disabled scripting. None of the commands in the scripting category may be used.");
            }

            Session session = context.getSession();
            GuildSpecification specification = context.getGuildContext().getSpecification(session);
            boolean enableScripting = guildPropertyManager
                .getPropertyValueOptional("enableScripting", Boolean.class, specification)
                .orElse(true);

            if (!enableScripting) {
                throw new InvalidCommandException("Scripting has been disabled for this guild. None of the commands in the scripting category may be used. " +
                    "Toggle the enable scripting property using the property command to enable / disable scripting.");
            }
        }

        if (!securityManager.askPermission(command.getIdentifier(), context.getMember())) {
            // if accessConfiguration were null we would not have got here
            AccessConfiguration accessConfiguration = guildManager.getAccessConfiguration(command.getIdentifier(), guild);
            //noinspection ConstantConditions
            throw new ForbiddenCommandException(user, command.getIdentifier(), accessConfiguration.getRoles(guild));
        }
    }
}
