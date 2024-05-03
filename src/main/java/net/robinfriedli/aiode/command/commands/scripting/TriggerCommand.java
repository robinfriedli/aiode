package net.robinfriedli.aiode.command.commands.scripting;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.managers.channel.middleman.AudioChannelManager;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.scripting.ScriptCommandRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TriggerCommand extends AbstractScriptCommand {

    public TriggerCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category, "trigger");
    }

    @Nullable
    @Override
    protected Map<String, ?> defineAdditionalVariables() {
        return Map.of(
            "voiceChannel", new DummyAudioChannel(),
            "command", new ScriptCommandRunner(getManager())
        );
    }

    @Override
    protected String triggerEvent() {
        return getArgumentValue("event");
    }

    private static class DummyAudioChannel implements AudioChannel {
        @NotNull
        @Override
        public Guild getGuild() {
            return null;
        }

        @NotNull
        @Override
        public AudioChannelManager<?, ?> getManager() {
            return null;
        }

        @NotNull
        @Override
        public String getName() {
            return "";
        }

        @NotNull
        @Override
        public ChannelType getType() {
            return null;
        }

        @NotNull
        @Override
        public JDA getJDA() {
            return null;
        }

        @NotNull
        @Override
        public AuditableRestAction<Void> delete() {
            return null;
        }

        @NotNull
        @Override
        public IPermissionContainer getPermissionContainer() {
            return null;
        }

        @Override
        public int getPositionRaw() {
            return 0;
        }

        @Nullable
        @Override
        public PermissionOverride getPermissionOverride(@NotNull IPermissionHolder permissionHolder) {
            return null;
        }

        @NotNull
        @Override
        public List<PermissionOverride> getPermissionOverrides() {
            return List.of();
        }

        @NotNull
        @Override
        public PermissionOverrideAction upsertPermissionOverride(@NotNull IPermissionHolder permissionHolder) {
            return null;
        }

        @Override
        public long getParentCategoryIdLong() {
            return 0;
        }

        @Override
        public boolean isSynced() {
            return false;
        }

        @NotNull
        @Override
        public ChannelAction<? extends StandardGuildChannel> createCopy(@NotNull Guild guild) {
            return null;
        }

        @NotNull
        @Override
        public ChannelAction<? extends StandardGuildChannel> createCopy() {
            return null;
        }

        @Override
        public int getBitrate() {
            return 0;
        }

        @Override
        public int getUserLimit() {
            return 0;
        }

        @Nullable
        @Override
        public String getRegionRaw() {
            return "";
        }

        @NotNull
        @Override
        public InviteAction createInvite() {
            return null;
        }

        @NotNull
        @Override
        public RestAction<List<Invite>> retrieveInvites() {
            return null;
        }

        @NotNull
        @Override
        public List<Member> getMembers() {
            return List.of();
        }

        @Override
        public int compareTo(@NotNull GuildChannel o) {
            return 0;
        }

        @Override
        public long getIdLong() {
            return 0;
        }
    }
}
