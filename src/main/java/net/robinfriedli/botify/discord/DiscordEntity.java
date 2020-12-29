package net.robinfriedli.botify.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.RestAction;
import net.robinfriedli.botify.exceptions.DiscordEntityInitialisationException;
import org.jetbrains.annotations.Nullable;

public abstract class DiscordEntity<T extends ISnowflake> {

    private final JDA jda;
    private final long id;

    public DiscordEntity(JDA jda, long id) {
        this.jda = jda;
        this.id = id;
    }

    /**
     * @return a rest action that retrieves the requested resource, may return null if the request cannot be constructed
     * because a dependent resource is null
     * @throws UnsupportedOperationException if there is no such request for this resource type, e.g. guilds, which are
     *                                       always retrieved from cache
     */
    @Nullable
    public abstract RestAction<? extends T> fetch() throws UnsupportedOperationException;

    /**
     * @return true if this resource can be fetched via rest request, i.e. if {@link #fetch()} does not throw an {@link UnsupportedOperationException}
     */
    public abstract boolean canFetch();

    @Nullable
    public abstract T getCached();

    @Nullable
    public T retrieve() {
        T cached = getCached();

        if (cached != null) {
            return cached;
        }

        if (!canFetch()) {
            return null;
        }

        RestAction<? extends T> fetch;
        try {
            fetch = fetch();
        } catch (InsufficientPermissionException e) {
            throw e;
        } catch (Exception e) {
            throw new DiscordEntityInitialisationException(e);
        }

        if (fetch == null) {
            return null;
        }

        try {
            return fetch.complete();
        } catch (ErrorResponseException e) {
            return null;
        } catch (InsufficientPermissionException e) {
            throw e;
        } catch (Exception e) {
            throw new DiscordEntityInitialisationException(e);
        }
    }

    public T get() {
        T obj = retrieve();

        if (obj == null) {
            throw new DiscordEntityInitialisationException(String.format("Could not retrieve %s with id %d", getClass().getSimpleName(), getId()));
        }

        return obj;
    }

    public JDA getJda() {
        return jda;
    }

    public long getId() {
        return id;
    }

    public String getIdString() {
        return Long.toUnsignedString(getId());
    }

    public static class Guild extends DiscordEntity<net.dv8tion.jda.api.entities.Guild> {

        public Guild(JDA jda, long id) {
            super(jda, id);
        }

        public Guild(net.dv8tion.jda.api.entities.Guild guild) {
            this(guild.getJDA(), guild.getIdLong());
        }

        @Override
        public RestAction<net.dv8tion.jda.api.entities.Guild> fetch() throws UnsupportedOperationException {
            throw new UnsupportedOperationException("Cannot create rest request to retrieve guilds");
        }

        @Override
        public boolean canFetch() {
            return false;
        }

        @Nullable
        @Override
        public net.dv8tion.jda.api.entities.Guild getCached() {
            return getJda().getGuildById(getId());
        }
    }

    public static class User extends DiscordEntity<net.dv8tion.jda.api.entities.User> {

        public User(JDA jda, long id) {
            super(jda, id);
        }

        public User(net.dv8tion.jda.api.entities.User user) {
            this(user.getJDA(), user.getIdLong());
        }

        @Override
        public RestAction<net.dv8tion.jda.api.entities.User> fetch() throws UnsupportedOperationException {
            return getJda().retrieveUserById(getId());
        }

        @Override
        public boolean canFetch() {
            return true;
        }

        @Nullable
        @Override
        public net.dv8tion.jda.api.entities.User getCached() {
            return getJda().getUserById(getId());
        }
    }

    public static class Member extends DiscordEntity<net.dv8tion.jda.api.entities.Member> {

        private final Guild guild;

        public Member(JDA jda, long id, Guild guild) {
            super(jda, id);
            this.guild = guild;
        }

        public Member(net.dv8tion.jda.api.entities.Member member) {
            this(member.getJDA(), member.getIdLong(), new Guild(member.getGuild()));
        }

        @Override
        @Nullable
        public RestAction<net.dv8tion.jda.api.entities.Member> fetch() throws UnsupportedOperationException {
            net.dv8tion.jda.api.entities.Guild guild = this.guild.retrieve();

            if (guild != null) {
                return guild.retrieveMemberById(getId());
            }

            return null;
        }

        @Override
        public boolean canFetch() {
            return true;
        }

        @Nullable
        @Override
        public net.dv8tion.jda.api.entities.Member getCached() {
            net.dv8tion.jda.api.entities.Guild guild = this.guild.getCached();

            if (guild != null) {
                guild.getMemberById(getId());
            }

            return null;
        }

        public Guild getGuild() {
            return guild;
        }
    }

    public static class MessageChannel extends DiscordEntity<net.dv8tion.jda.api.entities.MessageChannel> {

        private final User user;
        private final Guild guild;

        public MessageChannel(JDA jda, long id, User user) {
            super(jda, id);
            this.user = user;
            this.guild = null;
        }

        public MessageChannel(JDA jda, long id, Guild guild) {
            super(jda, id);
            this.user = null;
            this.guild = guild;
        }

        public static MessageChannel createForMessage(net.dv8tion.jda.api.entities.Message message) {
            return createForMessageChannel(message.getChannel());
        }

        public static MessageChannel createForMessageChannel(net.dv8tion.jda.api.entities.MessageChannel channel) {
            ChannelType type = channel.getType();
            if (type.isGuild()) {
                return new MessageChannel(channel.getJDA(), channel.getIdLong(), new Guild(((GuildChannel) channel).getGuild()));
            } else if (type == ChannelType.PRIVATE) {
                net.dv8tion.jda.api.entities.User user = ((net.dv8tion.jda.api.entities.PrivateChannel) channel).getUser();
                return new MessageChannel(channel.getJDA(), channel.getIdLong(), new User(user));
            }

            throw new UnsupportedOperationException("Message is from unsupported channel type " + type);
        }

        @Override
        public @Nullable RestAction<? extends net.dv8tion.jda.api.entities.MessageChannel> fetch() throws UnsupportedOperationException {
            if (user != null) {
                // fetch() never returns null for users
                //noinspection ConstantConditions
                return user.fetch().flatMap(net.dv8tion.jda.api.entities.User::openPrivateChannel);
            }
            throw new UnsupportedOperationException("Can only create rest request for private channels");
        }

        @Override
        public boolean canFetch() {
            return user != null;
        }

        @Nullable
        @Override
        public net.dv8tion.jda.api.entities.MessageChannel getCached() {
            if (guild != null) {
                net.dv8tion.jda.api.entities.Guild guildCached = guild.getCached();

                if (guildCached != null) {
                    return guildCached.getTextChannelById(getId());
                }
            }
            return null;
        }

        @Nullable
        public User getUser() {
            return user;
        }

        @Nullable
        public Guild getGuild() {
            return guild;
        }
    }

    public static class TextChannel extends DiscordEntity<net.dv8tion.jda.api.entities.TextChannel> {

        private final Guild guild;

        public TextChannel(JDA jda, long id, Guild guild) {
            super(jda, id);
            this.guild = guild;
        }

        public TextChannel(net.dv8tion.jda.api.entities.TextChannel textChannel) {
            this(textChannel.getJDA(), textChannel.getIdLong(), new Guild(textChannel.getGuild()));
        }

        @Override
        public @Nullable RestAction<? extends net.dv8tion.jda.api.entities.TextChannel> fetch() throws UnsupportedOperationException {
            throw new UnsupportedOperationException("Cannot create rest request to retrieve text channels");
        }

        @Override
        public boolean canFetch() {
            return false;
        }

        @Nullable
        @Override
        public net.dv8tion.jda.api.entities.TextChannel getCached() {
            net.dv8tion.jda.api.entities.Guild guildCached = guild.getCached();

            if (guildCached != null) {
                return guildCached.getTextChannelById(getId());
            }

            return null;
        }

        public Guild getGuild() {
            return guild;
        }
    }

    public static class PrivateChannel extends DiscordEntity<net.dv8tion.jda.api.entities.PrivateChannel> {

        private final User user;

        public PrivateChannel(JDA jda, long id, User user) {
            super(jda, id);
            this.user = user;
        }

        public PrivateChannel(net.dv8tion.jda.api.entities.PrivateChannel privateChannel) {
            this(privateChannel.getJDA(), privateChannel.getIdLong(), new User(privateChannel.getUser()));
        }

        @Override
        public @Nullable RestAction<? extends net.dv8tion.jda.api.entities.PrivateChannel> fetch() throws UnsupportedOperationException {
            // fetch() does not return null for users
            //noinspection ConstantConditions
            return user.fetch().flatMap(net.dv8tion.jda.api.entities.User::openPrivateChannel);
        }

        @Override
        public boolean canFetch() {
            return true;
        }

        @Nullable
        @Override
        public net.dv8tion.jda.api.entities.PrivateChannel getCached() {
            return null;
        }

        public User getUser() {
            return user;
        }
    }

    public static class Message extends DiscordEntity<net.dv8tion.jda.api.entities.Message> {

        private final MessageChannel channel;

        public Message(JDA jda, long id, MessageChannel channel) {
            super(jda, id);
            this.channel = channel;
        }

        public Message(net.dv8tion.jda.api.entities.Message message) {
            this(message.getJDA(), message.getIdLong(), MessageChannel.createForMessage(message));
        }

        @Override
        public @Nullable RestAction<? extends net.dv8tion.jda.api.entities.Message> fetch() throws UnsupportedOperationException {
            if (channel.canFetch()) {
                RestAction<? extends net.dv8tion.jda.api.entities.MessageChannel> channelFetch = channel.fetch();

                if (channelFetch != null) {
                    return channelFetch.flatMap(c -> c.retrieveMessageById(getId()));
                }
            }

            net.dv8tion.jda.api.entities.MessageChannel retrievedChannel = channel.retrieve();
            if (retrievedChannel != null) {
                return retrievedChannel.retrieveMessageById(getId());
            }

            return null;
        }

        @Override
        public boolean canFetch() {
            return true;
        }

        @Nullable
        @Override
        public net.dv8tion.jda.api.entities.Message getCached() {
            return null;
        }

        public MessageChannel getChannel() {
            return channel;
        }
    }

    public static class VoiceChannel extends DiscordEntity<net.dv8tion.jda.api.entities.VoiceChannel> {

        private final Guild guild;

        public VoiceChannel(JDA jda, long id, Guild guild) {
            super(jda, id);
            this.guild = guild;
        }

        public VoiceChannel(net.dv8tion.jda.api.entities.VoiceChannel voiceChannel) {
            this(voiceChannel.getJDA(), voiceChannel.getIdLong(), new Guild(voiceChannel.getGuild()));
        }

        @Override
        public @Nullable RestAction<? extends net.dv8tion.jda.api.entities.VoiceChannel> fetch() throws UnsupportedOperationException {
            throw new UnsupportedOperationException("Cannot create rest request to retrieve voice channels");
        }

        @Override
        public boolean canFetch() {
            return false;
        }

        @Nullable
        @Override
        public net.dv8tion.jda.api.entities.VoiceChannel getCached() {
            net.dv8tion.jda.api.entities.Guild guildCached = guild.getCached();

            if (guildCached != null) {
                return guildCached.getVoiceChannelById(getId());
            }

            return null;
        }
    }

}
