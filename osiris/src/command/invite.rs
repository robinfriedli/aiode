use diesel::{sql_types::VarChar, ExpressionMethods, QueryDsl, SelectableHelper};
use diesel_async::{scoped_futures::ScopedFutureExt, RunQueryDsl};
use lazy_static::lazy_static;
use serenity::all::{CommandInteraction, CreateEmbed, EditInteractionResponse};

use crate::{
    db::{acquire_db_connection, run_serializable_transaction},
    error::{Error, TransactionRuntimeError},
    glyph_api,
    model::{GuildSpecification, PrivateBotInstance},
    schema::{guild_specification, private_bot_instance},
};

use super::BotCommand;

lazy_static! {
    pub static ref AIODE_PUBLIC_INVITE_ENABLED: bool = std::env::var("OSIRIS_AIODE_PUBLIC_INVITE_ENABLED").map(|b| b.parse::<bool>().expect("OSIRIS_AIODE_PUBLIC_INVITE_ENABLED is not a valid bool")).unwrap_or(true);
    pub static ref AIODE_PUBLIC_INVITE: String = std::env::var("OSIRIS_AIODE_PUBLIC_INVITE").unwrap_or_else(|_| String::from("https://discordapp.com/api/oauth2/authorize?client_id=483377420494176258&permissions=70315072&scope=bot"));
}

pub struct InvitePublicCommand;

impl BotCommand for InvitePublicCommand {
    async fn run(_interaction: &CommandInteraction) -> Result<EditInteractionResponse, Error> {
        let mut embed = CreateEmbed::new();
        embed = embed.title("Public Invite");
        if *AIODE_PUBLIC_INVITE_ENABLED {
            embed = embed.description(format!("[Invite link]({})", AIODE_PUBLIC_INVITE.as_str()));
        } else {
            embed = embed.description("The public instance of aiode is disabled");
        }
        Ok(EditInteractionResponse::new().embed(embed))
    }
}

pub struct InvitePrivateCommand;

impl BotCommand for InvitePrivateCommand {
    async fn run(interaction: &CommandInteraction) -> Result<EditInteractionResponse, Error> {
        if !glyph_api::user_is_aiode_supporter(interaction.user.id).await {
            return Err(Error::UserIsNotSupporterError);
        }

        let mut connection = acquire_db_connection().await?;
        run_serializable_transaction(&mut connection, |connection| {
            async move {
                let guild_id = interaction.guild_id.ok_or_else(|| {
                    TransactionRuntimeError::Rollback(Error::InvalidCommandError(String::from(
                        "This command can only be used within a guild",
                    )))
                })?;
                let guild_id_str = guild_id.get().to_string();
                let assigned_bot_instance = guild_specification::table
                    .left_join(private_bot_instance::table)
                    .select(Option::<PrivateBotInstance>::as_select())
                    .filter(guild_specification::guild_id.eq(&guild_id_str))
                    .first::<Option<PrivateBotInstance>>(connection)
                    .await?;

                if let Some(assigned_bot_instance) = assigned_bot_instance {
                    Ok(EditInteractionResponse::new().embed(CreateEmbed::new().title("Private Invite").description(format!("[Invite link]({})", assigned_bot_instance.invite_link))))
                } else {
                    let updated_specification = diesel::sql_query(r#"
                        UPDATE guild_specification SET assigned_private_bot_instance = (
                            SELECT pbi.identifier
                            FROM private_bot_instance pbi
                            LEFT JOIN guild_specification gs2 ON pbi.identifier = gs2.assigned_private_bot_instance
                            GROUP BY pbi.identifier, pbi.server_limit
                            HAVING COUNT(gs2.guild_id) < pbi.server_limit
                            ORDER BY COUNT(gs2.guild_id) ASC
                            LIMIT 1
                        )
                        WHERE guild_id = $1 RETURNING *
                    "#)
                    .bind::<VarChar, _>(&guild_id_str)
                    .get_result::<GuildSpecification>(connection)
                    .await?;

                    if let Some(ref assigned_bot_instance) = updated_specification.assigned_private_bot_instance {
                        let bot_instance = private_bot_instance::table.filter(private_bot_instance::identifier.eq(assigned_bot_instance)).first::<PrivateBotInstance>(connection).await?;
                        Ok(EditInteractionResponse::new().embed(CreateEmbed::new().title("Private Invite").description(format!("[Invite link]({})", bot_instance.invite_link))))
                    } else {
                        Err(TransactionRuntimeError::Rollback(Error::NoPrivateBotAvailableError))
                    }
                }
            }
            .scope_boxed()
        })
        .await
    }
}
