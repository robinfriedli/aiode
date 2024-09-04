package net.robinfriedli.aiode.command.commands.general;

import com.google.common.base.Strings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.command.SecurityManager;
import net.robinfriedli.aiode.entities.GuildSpecification;
import net.robinfriedli.aiode.entities.PrivateBotInstance;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;

public class InviteCommand extends AbstractCommand {

    public InviteCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandBody, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandBody, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() throws Exception {
        if (argumentSet("public")) {
            String inviteLink = Aiode.get().getSpringPropertiesConfig().getPrivateProperty("aiode.preferences.invite_link");
            if (Strings.isNullOrEmpty(inviteLink)) {
                inviteLink = "https://discordapp.com/api/oauth2/authorize?client_id=483377420494176258&permissions=70315072&scope=bot";
            }
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Public Invite");
            embedBuilder.setDescription(String.format("[Invite link](%s)", inviteLink));
            sendMessage(embedBuilder);
        } else if (argumentSet("private")) {
            SecurityManager securityManager = Aiode.get().getSecurityManager();
            CommandContext context = getContext();
            if (!securityManager.isSupporter(context.getUser())) {
                throw new InvalidCommandException("You must be a [supporter](https://ko-fi.com/R5R0XAC5J) to invite a private bot. To be verified as a supporter, you must have the supporters role in the [aiode discord](https://discord.gg/gdc25AG). This role is assigned automatically if your Ko-fi account is connected to discord.");
            }

            consumeSession(session -> {
                GuildSpecification specification = context.getGuildContext().getSpecification(session);
                PrivateBotInstance privateBotInstance = specification.getPrivateBotInstance();
                if (privateBotInstance != null) {
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setTitle("Private Invite");
                    embedBuilder.setDescription(String.format("[Invite link](%s)", privateBotInstance.getInviteLink()));
                    sendMessage(embedBuilder);
                } else {
                    GuildSpecification updatedGuildSpecification = session.createNativeQuery(
                        "UPDATE guild_specification SET assigned_private_bot_instance = (" +
                            "    SELECT pbi.identifier" +
                            "    FROM private_bot_instance pbi" +
                            "    LEFT JOIN guild_specification gs2 ON pbi.identifier = gs2.assigned_private_bot_instance" +
                            "    GROUP BY pbi.identifier, pbi.server_limit" +
                            "    HAVING COUNT(gs2.guild_id) < pbi.server_limit" +
                            "    ORDER BY COUNT(gs2.guild_id) ASC" +
                            "    LIMIT 1 " +
                            ") "
                            + "WHERE guild_id = ? RETURNING *",
                        GuildSpecification.class
                    ).setParameter(1, context.getGuild().getId()).getSingleResultOrNull();

                    session.refresh(updatedGuildSpecification);
                    PrivateBotInstance assignedBotInstance = updatedGuildSpecification.getPrivateBotInstance();
                    if (assignedBotInstance != null) {
                        EmbedBuilder embedBuilder = new EmbedBuilder();
                        embedBuilder.setTitle("Private Invite");
                        embedBuilder.setDescription(String.format("[Invite link](%s)", assignedBotInstance.getInviteLink()));
                        sendMessage(embedBuilder);
                    } else {
                        throw new InvalidCommandException("There is currently no private bot instance available. Check the [aiode discord](https://discord.gg/gdc25AG) to learn when new availability is added.");
                    }
                }
            });
        } else {
            boolean supporter = Aiode.get().getSecurityManager().isSupporter(getContext().getUser());
            GuildSpecification specification = getContext().getGuildContext().getSpecification();
            String publicInviteLink = Aiode.get().getSpringPropertiesConfig().getPrivateProperty("aiode.preferences.invite_link");
            if (Strings.isNullOrEmpty(publicInviteLink)) {
                publicInviteLink = "https://discordapp.com/api/oauth2/authorize?client_id=483377420494176258&permissions=70315072&scope=bot";
            }

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Bot Invite Links");
            embedBuilder.setDescription("Provides invite links for the public bot, as well as private bots for [supporters](https://ko-fi.com/R5R0XAC5J). " +
                "Private bots are limited to 100 servers per bot, with more bots being created on demand. Private bots feature YouTube bot detection circumvention, enable traditional text commands with custom prefixes and provide access to the bot's scripting sandbox. " +
                (supporter ? "Invoke this command with the 'private' option to assign a private bot to your server and receive an invitation." : "You must be a [supporter](https://ko-fi.com/R5R0XAC5J) to invite a private bot. To be verified as a supporter, you must have the supporters role in the [aiode discord](https://discord.gg/gdc25AG). This role is assigned automatically if your Ko-fi account is connected to discord."));
            embedBuilder.addField("Public Invite", String.format("[Invite link](%s)", publicInviteLink), true);

            PrivateBotInstance privateBotInstance = specification.getPrivateBotInstance();
            if (privateBotInstance != null) {
                embedBuilder.addField("Private Invite", String.format("[Invite link](%s)", privateBotInstance.getInviteLink()), true);
            } else {
                Integer availableSlots = getContext().getSession().createNativeQuery(
                    "SELECT (SELECT COALESCE(SUM(server_limit), 0) FROM private_bot_instance) - (SELECT COUNT(*) FROM guild_specification WHERE assigned_private_bot_instance IS NOT NULL) AS available_slots",
                    Integer.class
                ).getSingleResult();

                embedBuilder.addField(
                    "Private Invite",
                    String.format("%d slots available", Math.max(availableSlots, 0)),
                    true
                );
            }

            sendMessage(embedBuilder);
        }
    }

    @Override
    public void onSuccess() {
    }

}
