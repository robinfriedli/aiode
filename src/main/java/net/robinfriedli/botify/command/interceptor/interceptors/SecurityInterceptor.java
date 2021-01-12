package net.robinfriedli.botify.command.interceptor.interceptors;

import java.util.Collection;

import net.dv8tion.jda.api.entities.Member;
import net.robinfriedli.botify.boot.SpringPropertiesConfig;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.PermissionTarget;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.command.argument.ArgumentController;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.discord.property.GuildPropertyManager;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import org.hibernate.Session;

/**
 * Interceptor that checks whether a member is allowed to use the current command
 */
public class SecurityInterceptor extends AbstractChainableCommandInterceptor {

    private final GuildPropertyManager guildPropertyManager;
    private final SecurityManager securityManager;
    private final SpringPropertiesConfig springPropertiesConfig;

    public SecurityInterceptor(
        CommandInterceptorContribution contribution,
        CommandInterceptor next,
        GuildPropertyManager guildPropertyManager,
        SecurityManager securityManager,
        SpringPropertiesConfig springPropertiesConfig
    ) {
        super(contribution, next);
        this.guildPropertyManager = guildPropertyManager;
        this.securityManager = securityManager;
        this.springPropertiesConfig = springPropertiesConfig;
    }

    @Override
    public void performChained(Command command) {
        try {
            CommandContext context = command.getContext();

            if (command instanceof AbstractCommand && ((AbstractCommand) command).getCategory() == AbstractCommand.Category.SCRIPTING) {
                if (!springPropertiesConfig.requireApplicationProperty(Boolean.class, "botify.preferences.enable_scripting")) {
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

            PermissionTarget permissionTarget = command.getPermissionTarget();

            Member member = context.getMember();
            securityManager.ensurePermission(permissionTarget, member);

            if (command instanceof AbstractCommand) {
                AbstractCommand textCommand = (AbstractCommand) command;
                Collection<ArgumentController.ArgumentUsage> argumentUsages = textCommand.getArgumentController().getUsedArguments().values();

                for (ArgumentController.ArgumentUsage argumentUsage : argumentUsages) {
                    PermissionTarget argumentPermissionTarget = argumentUsage.getArgument().getPermissionTarget();
                    if (argumentPermissionTarget != null) {
                        securityManager.ensurePermission(argumentPermissionTarget, member);
                    }
                }
            }
        } catch (ForbiddenCommandException | InvalidCommandException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Failed to verify command security due to an unexpected exception", e);
        }
    }
}
