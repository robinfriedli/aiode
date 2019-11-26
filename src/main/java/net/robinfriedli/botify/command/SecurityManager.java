package net.robinfriedli.botify.command;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manager that evaluates permission for a specific action represented by a command identifier for the given member or
 * checks whether a user has admin privileges as configured in the settings-private.properties file.
 */
@Component
public class SecurityManager {

    private final GuildManager guildManager;
    @Value("#{'${botify.security.admin_users}'.split(',')}")
    private List<String> adminUserIds;

    public SecurityManager(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    public boolean askPermission(String commandIdentifier, Member member) {
        return isAdmin(member.getUser()) || guildManager.checkAccess(commandIdentifier, member);
    }

    public void ensurePermission(String commandIdentifier, Member member) throws ForbiddenCommandException {
        if (!askPermission(commandIdentifier, member)) {
            Guild guild = member.getGuild();
            AccessConfiguration violatingConfiguration = guildManager.getAccessConfiguration(commandIdentifier, guild);
            // if it were null we would not have got here
            //noinspection ConstantConditions
            throw new ForbiddenCommandException(member.getUser(), commandIdentifier, violatingConfiguration.getRoles(guild));
        }
    }

    public boolean isAdmin(User user) {
        return adminUserIds.contains(user.getId());
    }

}
