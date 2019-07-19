package net.robinfriedli.botify.command;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;
import net.robinfriedli.botify.util.PropertiesLoadingService;

public class SecurityManager {

    private final GuildManager guildManager;

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
        String adminUserString = PropertiesLoadingService.loadProperty("ADMIN_USERS");
        if (!Strings.isNullOrEmpty(adminUserString)) {
            Splitter splitter = Splitter.on(",").trimResults().omitEmptyStrings();
            return splitter.splitToList(adminUserString).contains(user.getId());
        }

        return false;
    }

}
