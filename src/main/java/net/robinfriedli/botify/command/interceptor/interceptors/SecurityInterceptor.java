package net.robinfriedli.botify.command.interceptor.interceptors;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;

public class SecurityInterceptor extends AbstractChainableCommandInterceptor {

    private final GuildManager guildManager;
    private final SecurityManager securityManager;

    public SecurityInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next, GuildManager guildManager, SecurityManager securityManager) {
        super(contribution, next);
        this.guildManager = guildManager;
        this.securityManager = securityManager;
    }

    @Override
    public void performChained(AbstractCommand command) {
        CommandContext context = command.getContext();
        User user = context.getUser();
        Guild guild = context.getGuild();
        Member member = guild.getMember(user);
        if (!securityManager.askPermission(command.getIdentifier(), member)) {
            // if accessConfiguration were null we would not have got here
            AccessConfiguration accessConfiguration = guildManager.getAccessConfiguration(command.getIdentifier(), guild);
            //noinspection ConstantConditions
            throw new ForbiddenCommandException(user, command.getIdentifier(), accessConfiguration.getRoles(guild));
        }
    }

}
