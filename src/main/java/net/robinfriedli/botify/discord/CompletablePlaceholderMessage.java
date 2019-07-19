package net.robinfriedli.botify.discord;

import java.time.OffsetDateTime;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import net.dv8tion.jda.client.entities.Group;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.IMentionable;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageActivity;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.MessageReaction;
import net.dv8tion.jda.core.entities.MessageType;
import net.dv8tion.jda.core.entities.PrivateChannel;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.requests.RestAction;
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.core.requests.restaction.MessageAction;

/**
 * Mocks a discord message that has not been loaded yet.
 */
public class CompletablePlaceholderMessage implements Message {

    private final CompletableFuture<Message> futureMessage;

    public CompletablePlaceholderMessage() {
        futureMessage = new CompletableFuture<>();
    }

    public void complete(Message message) {
        futureMessage.complete(message);
    }

    public void completeExceptionally(Throwable e) {
        futureMessage.completeExceptionally(e);
    }

    @Override
    public List<User> getMentionedUsers() {
        try {
            return getChecked().getMentionedUsers();
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<TextChannel> getMentionedChannels() {
        try {
            return getChecked().getMentionedChannels();
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<Role> getMentionedRoles() {
        try {
            return getChecked().getMentionedRoles();
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<Member> getMentionedMembers(Guild guild) {
        try {
            return getChecked().getMentionedMembers(guild);
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<Member> getMentionedMembers() {
        try {
            return getChecked().getMentionedMembers();
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<IMentionable> getMentions(MentionType... mentionTypes) {
        try {
            return getChecked().getMentions(mentionTypes);
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public boolean isMentioned(IMentionable iMentionable, MentionType... mentionTypes) {
        try {
            return getChecked().isMentioned(iMentionable, mentionTypes);
        } catch (CancellationException e) {
            return false;
        }
    }

    @Override
    public boolean mentionsEveryone() {
        try {
            return getChecked().mentionsEveryone();
        } catch (CancellationException e) {
            return false;
        }
    }

    @Override
    public boolean isEdited() {
        try {
            return getChecked().isEdited();
        } catch (CancellationException e) {
            return false;
        }
    }

    @Override
    public OffsetDateTime getEditedTime() {
        try {
            return getChecked().getEditedTime();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public User getAuthor() {
        try {
            return getChecked().getAuthor();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public Member getMember() {
        try {
            return getChecked().getMember();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public String getJumpUrl() {
        try {
            return getChecked().getJumpUrl();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public String getContentDisplay() {
        try {
            return getChecked().getContentDisplay();
        } catch (CancellationException e) {
            return "";
        }
    }

    @Override
    public String getContentRaw() {
        try {
            return getChecked().getContentRaw();
        } catch (CancellationException e) {
            return "";
        }
    }

    @Override
    public String getContentStripped() {
        try {
            return getChecked().getContentStripped();
        } catch (CancellationException e) {
            return "";
        }
    }

    @Override
    public List<String> getInvites() {
        try {
            return getChecked().getInvites();
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public String getNonce() {
        try {
            return getChecked().getNonce();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public boolean isFromType(ChannelType channelType) {
        try {
            return getChecked().isFromType(channelType);
        } catch (CancellationException e) {
            return false;
        }
    }

    @Override
    public ChannelType getChannelType() {
        try {
            return getChecked().getChannelType();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public boolean isWebhookMessage() {
        try {
            return getChecked().isWebhookMessage();
        } catch (CancellationException e) {
            return false;
        }
    }

    @Override
    public MessageChannel getChannel() {
        try {
            return getChecked().getChannel();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public PrivateChannel getPrivateChannel() {
        try {
            return getChecked().getPrivateChannel();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public Group getGroup() {
        try {
            return getChecked().getGroup();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public TextChannel getTextChannel() {
        try {
            return getChecked().getTextChannel();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public Category getCategory() {
        try {
            return getChecked().getCategory();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public Guild getGuild() {
        try {
            return getChecked().getGuild();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public List<Attachment> getAttachments() {
        try {
            return getChecked().getAttachments();
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<MessageEmbed> getEmbeds() {
        try {
            return getChecked().getEmbeds();
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<Emote> getEmotes() {
        try {
            return getChecked().getEmotes();
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public List<MessageReaction> getReactions() {
        try {
            return getChecked().getReactions();
        } catch (CancellationException e) {
            return Lists.newArrayList();
        }
    }

    @Override
    public boolean isTTS() {
        try {
            return getChecked().isTTS();
        } catch (CancellationException e) {
            return false;
        }
    }

    @Nullable
    @Override
    public MessageActivity getActivity() {
        try {
            return getChecked().getActivity();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public MessageAction editMessage(CharSequence charSequence) {
        try {
            return getChecked().editMessage(charSequence);
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public MessageAction editMessage(MessageEmbed messageEmbed) {
        try {
            return getChecked().editMessage(messageEmbed);
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public MessageAction editMessageFormat(String s, Object... objects) {
        try {
            return getChecked().editMessageFormat(s, objects);
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public MessageAction editMessage(Message message) {
        try {
            return getChecked().editMessage(message);
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public AuditableRestAction<Void> delete() {
        try {
            return getChecked().delete();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public JDA getJDA() {
        try {
            return getChecked().getJDA();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public boolean isPinned() {
        try {
            return getChecked().isPinned();
        } catch (CancellationException e) {
            return false;
        }
    }

    @Override
    public RestAction<Void> pin() {
        try {
            return getChecked().pin();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public RestAction<Void> unpin() {
        try {
            return getChecked().unpin();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public RestAction<Void> addReaction(Emote emote) {
        try {
            return getChecked().addReaction(emote);
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public RestAction<Void> addReaction(String s) {
        try {
            return getChecked().addReaction(s);
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public RestAction<Void> clearReactions() {
        try {
            return getChecked().clearReactions();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public MessageType getType() {
        try {
            return getChecked().getType();
        } catch (CancellationException e) {
            return null;
        }
    }

    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
        try {
            getChecked().formatTo(formatter, flags, width, precision);
        } catch (CancellationException ignored) {
        }
    }

    @Override
    public long getIdLong() {
        try {
            return getChecked().getIdLong();
        } catch (CancellationException e) {
            return 0;
        }
    }

    private Message getChecked() {
        try {
            return futureMessage.get(1, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new CancellationException();
        }
    }

}
