package net.robinfriedli.botify.command.interceptors;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandInterceptor;
import net.robinfriedli.botify.discord.GuildSpecificationManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;

public class SecurityInterceptor implements CommandInterceptor {

    @Override
    public void intercept(AbstractCommand command) {
        GuildSpecificationManager guildManager = command.getManager().getGuildManager();
        CommandContext context = command.getContext();
        User user = context.getUser();
        Guild guild = context.getGuild();
        Member member = guild.getMember(user);
        if (!guildManager.checkAccess(command.getIdentifier(), member)) {
            // if accessConfiguration were null we would not have got here
            AccessConfiguration accessConfiguration = guildManager.getAccessConfiguration(command.getIdentifier(), guild);
            throw new ForbiddenCommandException(user, command.getIdentifier(), accessConfiguration.getRoles(guild));
        }
    }
}
