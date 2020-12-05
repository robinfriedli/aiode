package net.robinfriedli.botify.discord;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.collections4.Bag;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageActivity;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction;
import org.jetbrains.annotations.NotNull;

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

    public Message unwrap() {
        try {
            return futureMessage.get(1, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("Wrapped message failed to load", e);
        }
    }

    @Nullable
    @Override
    public Message getReferencedMessage() {
        return unwrap().getReferencedMessage();
    }

    @Nonnull
    @Override
    public List<User> getMentionedUsers() {
        return unwrap().getMentionedUsers();
    }

    @Nonnull
    @Override
    public Bag<User> getMentionedUsersBag() {
        return unwrap().getMentionedUsersBag();
    }

    @Nonnull
    @Override
    public List<TextChannel> getMentionedChannels() {
        return unwrap().getMentionedChannels();
    }

    @Nonnull
    @Override
    public Bag<TextChannel> getMentionedChannelsBag() {
        return unwrap().getMentionedChannelsBag();
    }

    @Nonnull
    @Override
    public List<Role> getMentionedRoles() {
        return unwrap().getMentionedRoles();
    }

    @Nonnull
    @Override
    public Bag<Role> getMentionedRolesBag() {
        return unwrap().getMentionedRolesBag();
    }

    @Nonnull
    @Override
    public List<Member> getMentionedMembers(@Nonnull Guild guild) {
        return unwrap().getMentionedMembers(guild);
    }

    @Nonnull
    @Override
    public List<Member> getMentionedMembers() {
        return unwrap().getMentionedMembers();
    }

    @Nonnull
    @Override
    public List<IMentionable> getMentions(@Nonnull MentionType... mentionTypes) {
        return unwrap().getMentions(mentionTypes);
    }

    @Override
    public boolean isMentioned(@Nonnull IMentionable iMentionable, @Nonnull MentionType... mentionTypes) {
        return unwrap().isMentioned(iMentionable, mentionTypes);
    }

    @Override
    public boolean mentionsEveryone() {
        return unwrap().mentionsEveryone();
    }

    @Override
    public boolean isEdited() {
        return unwrap().isEdited();
    }

    @Nullable
    @Override
    public OffsetDateTime getTimeEdited() {
        return unwrap().getTimeEdited();
    }

    @Nonnull
    @Override
    public User getAuthor() {
        return unwrap().getAuthor();
    }

    @Nullable
    @Override
    public Member getMember() {
        return unwrap().getMember();
    }

    @Nonnull
    @Override
    public String getJumpUrl() {
        return unwrap().getJumpUrl();
    }

    @Nonnull
    @Override
    public String getContentDisplay() {
        return unwrap().getContentDisplay();
    }

    @Nonnull
    @Override
    public String getContentRaw() {
        return unwrap().getContentRaw();
    }

    @Nonnull
    @Override
    public String getContentStripped() {
        return unwrap().getContentStripped();
    }

    @Nonnull
    @Override
    public List<String> getInvites() {
        return unwrap().getInvites();
    }

    @Nullable
    @Override
    public String getNonce() {
        return unwrap().getNonce();
    }

    @Override
    public boolean isFromType(@Nonnull ChannelType channelType) {
        return unwrap().isFromType(channelType);
    }

    @Nonnull
    @Override
    public ChannelType getChannelType() {
        return unwrap().getChannelType();
    }

    @Override
    public boolean isWebhookMessage() {
        return unwrap().isWebhookMessage();
    }

    @Nonnull
    @Override
    public MessageChannel getChannel() {
        return unwrap().getChannel();
    }

    @Nonnull
    @Override
    public PrivateChannel getPrivateChannel() {
        return unwrap().getPrivateChannel();
    }

    @Nonnull
    @Override
    public TextChannel getTextChannel() {
        return unwrap().getTextChannel();
    }

    @Nullable
    @Override
    public Category getCategory() {
        return unwrap().getCategory();
    }

    @Nonnull
    @Override
    public Guild getGuild() {
        return unwrap().getGuild();
    }

    @Nonnull
    @Override
    public List<Attachment> getAttachments() {
        return unwrap().getAttachments();
    }

    @Nonnull
    @Override
    public List<MessageEmbed> getEmbeds() {
        return unwrap().getEmbeds();
    }

    @Nonnull
    @Override
    public List<Emote> getEmotes() {
        return unwrap().getEmotes();
    }

    @Nonnull
    @Override
    public Bag<Emote> getEmotesBag() {
        return unwrap().getEmotesBag();
    }

    @Nonnull
    @Override
    public List<MessageReaction> getReactions() {
        return unwrap().getReactions();
    }

    @Override
    public boolean isTTS() {
        return unwrap().isTTS();
    }

    @Nullable
    @Override
    public MessageActivity getActivity() {
        return unwrap().getActivity();
    }

    @Nonnull
    @Override
    public MessageAction editMessage(@Nonnull CharSequence charSequence) {
        return unwrap().editMessage(charSequence);
    }

    @Nonnull
    @Override
    public MessageAction editMessage(@Nonnull MessageEmbed messageEmbed) {
        return unwrap().editMessage(messageEmbed);
    }

    @Nonnull
    @Override
    public MessageAction editMessageFormat(@Nonnull String s, @Nonnull Object... objects) {
        return unwrap().editMessageFormat(s, objects);
    }

    @Nonnull
    @Override
    public MessageAction editMessage(@Nonnull Message message) {
        return unwrap().editMessage(message);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> delete() {
        return unwrap().delete();
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return unwrap().getJDA();
    }

    @Override
    public boolean isPinned() {
        return unwrap().isPinned();
    }

    @Nonnull
    @Override
    public RestAction<Void> pin() {
        return unwrap().pin();
    }

    @Nonnull
    @Override
    public RestAction<Void> unpin() {
        return unwrap().unpin();
    }

    @Nonnull
    @Override
    public RestAction<Void> addReaction(@Nonnull Emote emote) {
        return unwrap().addReaction(emote);
    }

    @Nonnull
    @Override
    public RestAction<Void> addReaction(@Nonnull String s) {
        return unwrap().addReaction(s);
    }

    @Nonnull
    @Override
    public RestAction<Void> clearReactions() {
        return unwrap().clearReactions();
    }

    @Nonnull
    @Override
    public RestAction<Void> clearReactions(@Nonnull String s) {
        return unwrap().clearReactions(s);
    }

    @Nonnull
    @Override
    public RestAction<Void> clearReactions(@Nonnull Emote emote) {
        return unwrap().clearReactions(emote);
    }

    @Nonnull
    @Override
    public RestAction<Void> removeReaction(@Nonnull Emote emote) {
        return unwrap().removeReaction(emote);
    }

    @Nonnull
    @Override
    public RestAction<Void> removeReaction(@Nonnull Emote emote, @Nonnull User user) {
        return unwrap().removeReaction(emote, user);
    }

    @Nonnull
    @Override
    public RestAction<Void> removeReaction(@Nonnull String s) {
        return unwrap().removeReaction(s);
    }

    @Nonnull
    @Override
    public RestAction<Void> removeReaction(@Nonnull String s, @Nonnull User user) {
        return unwrap().removeReaction(s, user);
    }

    @Nonnull
    @Override
    public ReactionPaginationAction retrieveReactionUsers(@Nonnull Emote emote) {
        return unwrap().retrieveReactionUsers(emote);
    }

    @Nonnull
    @Override
    public ReactionPaginationAction retrieveReactionUsers(@Nonnull String s) {
        return unwrap().retrieveReactionUsers(s);
    }

    @Nullable
    @Override
    public MessageReaction.ReactionEmote getReactionByUnicode(@Nonnull String s) {
        return unwrap().getReactionByUnicode(s);
    }

    @Nullable
    @Override
    public MessageReaction.ReactionEmote getReactionById(@Nonnull String s) {
        return unwrap().getReactionById(s);
    }

    @Nullable
    @Override
    public MessageReaction.ReactionEmote getReactionById(long l) {
        return unwrap().getReactionById(l);
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> suppressEmbeds(boolean b) {
        return unwrap().suppressEmbeds(b);
    }

    @NotNull
    @Override
    public RestAction<Message> crosspost() {
        return unwrap().crosspost();
    }

    @Override
    public boolean isSuppressedEmbeds() {
        return unwrap().isSuppressedEmbeds();
    }

    @Nonnull
    @Override
    public EnumSet<MessageFlag> getFlags() {
        return unwrap().getFlags();
    }

    @Nonnull
    @Override
    public MessageType getType() {
        return unwrap().getType();
    }

    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
        unwrap().formatTo(formatter, flags, width, precision);
    }

    @Override
    public long getIdLong() {
        return unwrap().getIdLong();
    }
}
