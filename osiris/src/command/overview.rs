use std::cmp::max;

use diesel::{sql_types::BigInt, ExpressionMethods, QueryDsl, QueryableByName, SelectableHelper};
use diesel_async::{scoped_futures::ScopedFutureExt, RunQueryDsl};
use serenity::all::{CommandInteraction, CreateEmbed, EditInteractionResponse};

use crate::{
    command::invite::{AIODE_PUBLIC_INVITE, AIODE_PUBLIC_INVITE_ENABLED},
    db::{acquire_db_connection, run_retryable_transaction},
    error::{Error, TransactionRuntimeError},
    glyph_api,
    model::PrivateBotInstance,
    schema::{guild_specification, private_bot_instance},
};

use super::BotCommand;

pub struct OverviewCommand;

impl BotCommand for OverviewCommand {
    async fn run(interaction: &CommandInteraction) -> Result<EditInteractionResponse, Error> {
        let is_supporter = glyph_api::user_is_aiode_supporter(interaction.user.id).await;

        let mut embed = CreateEmbed::new()
            .title("Aiode Invite Links")
            .description(
                "Provides invite links for the public bot, as well as private bots for [supporters](https://ko-fi.com/R5R0XAC5J). ".to_owned()
                + "Private bots are limited to 100 servers per bot, with more bots being created on demand. Private bots feature YouTube bot detection circumvention, enable traditional text commands with custom prefixes and provide access to the bot's scripting sandbox. "
                + if is_supporter { "Invoke the 'invite private' command to assign a private bot to your server and receive an invitation." } else { "You must be a [supporter](https://ko-fi.com/R5R0XAC5J) to invite a private bot. To be verified as a supporter, you must have the supporters role in the [aiode discord](https://discord.gg/gdc25AG). This role is assigned automatically if your Ko-fi account is connected to discord." }
            );

        embed = embed.field(
            "Public Invite",
            if *AIODE_PUBLIC_INVITE_ENABLED {
                format!("[Invite link]({})", AIODE_PUBLIC_INVITE.as_str())
            } else {
                "The public instance of aiode is disabled".to_owned()
            },
            true,
        );

        let mut connection = acquire_db_connection().await?;
        let assigned_bot_instance = run_retryable_transaction(&mut connection, |connection| {
            async move {
                let guild_id = interaction.guild_id.ok_or_else(|| {
                    TransactionRuntimeError::Rollback(Error::InvalidCommandError(String::from(
                        "This command can only be used within a guild",
                    )))
                })?;
                let guild_id_str = guild_id.get().to_string();

                guild_specification::table
                    .left_join(private_bot_instance::table)
                    .select(Option::<PrivateBotInstance>::as_select())
                    .filter(guild_specification::guild_id.eq(&guild_id_str))
                    .first::<Option<PrivateBotInstance>>(connection)
                    .await
                    .map_err(TransactionRuntimeError::from)
            }
            .scope_boxed()
        })
        .await?;

        if let Some(ref assigned_bot_instance) = assigned_bot_instance {
            embed = embed.field(
                "Private Invite",
                format!("[Invite link]({})", assigned_bot_instance.invite_link),
                true,
            );
        } else {
            let available_slots = diesel::sql_query("SELECT (SELECT COALESCE(SUM(server_limit), 0) FROM private_bot_instance) - (SELECT COUNT(*) FROM guild_specification WHERE assigned_private_bot_instance IS NOT NULL) AS available_slots").load::<AvailableSlotCountResult>(&mut connection).await?;
            embed = embed.field(
                "Private Invite",
                format!(
                    "{} slots available",
                    max(
                        // disambiguate .first() call to use slice::first instead of diesel's RunQueryDsl
                        available_slots[..]
                            .first()
                            .map(|s| s.available_slots)
                            .unwrap_or(0),
                        0
                    )
                ),
                true,
            );
        }

        Ok(EditInteractionResponse::new().embed(embed))
    }
}

#[derive(QueryableByName)]
struct AvailableSlotCountResult {
    #[diesel(sql_type = BigInt)]
    available_slots: i64,
}
