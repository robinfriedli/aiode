use crate::{
    command::{self, BotCommand},
    db::acquire_db_connection,
    error::Error,
    model::{GuildSpecification, NewGuildSpecification},
    schema::guild_specification,
};
use diesel::{ExpressionMethods, OptionalExtension, QueryDsl};
use diesel_async::RunQueryDsl;
use serenity::all::GuildId;
use serenity::{
    all::{
        Colour, Command, CommandOptionType, Context, CreateCommand, CreateCommandOption,
        CreateEmbed, CreateInteractionResponse, CreateInteractionResponseMessage,
        EditInteractionResponse, EventHandler, Guild, Interaction, Ready,
    },
    async_trait,
};

pub struct DiscordEventHandler;

#[async_trait]
impl EventHandler for DiscordEventHandler {
    async fn ready(&self, ctx: Context, data_about_bot: Ready) {
        log::info!("Serenity client connected with data {data_about_bot:?}");

        if let Err(e) = Command::create_global_command(
            &ctx.http,
            CreateCommand::new("help").description("Receive help with the bot."),
        )
        .await
        {
            log::error!("Failed to create global command: {e}");
        }

        if let Err(e) = Command::create_global_command(
            &ctx.http,
            CreateCommand::new("invite")
                .description("Check for available aiode instances and invite one.")
                .dm_permission(false)
                .add_option(CreateCommandOption::new(
                    CommandOptionType::SubCommand,
                    "public",
                    "Get an invite link for the public aiode instance",
                ))
                .add_option(CreateCommandOption::new(
                    CommandOptionType::SubCommand,
                    "private",
                    "Get an invite link for the public aiode instance",
                )),
        )
        .await
        {
            log::error!("Failed to create global command: {e}");
        }

        if let Err(e) = Command::create_global_command(
            &ctx.http,
            CreateCommand::new("overview")
                .description("Get an overview of avalaible aiode invite links.")
                .dm_permission(false),
        )
        .await
        {
            log::error!("Failed to create global command: {e}");
        }
    }

    async fn shards_ready(&self, _ctx: Context, total_shards: u32) {
        log::info!("All {total_shards} shards are ready.");
    }

    async fn interaction_create(&self, ctx: Context, interaction: Interaction) {
        if let Interaction::Command(command) = interaction {
            if let Some(guild_id) = command.guild_id {
                match create_guild_specification_if_missing(guild_id, &ctx).await {
                    Ok(created) => {
                        if created {
                            log::info!(
                                "Created specification for missing guild \"{}\" ({})",
                                get_name_for_guild(&ctx, guild_id).await.unwrap_or_default(),
                                guild_id.get()
                            );
                        }
                    }
                    Err(e) => log::error!("Failed to check for missing guild specification: {e}"),
                }
            }

            let defer_response = CreateInteractionResponse::Defer(
                CreateInteractionResponseMessage::new().content("Loading"),
            );
            if let Err(e) = command.create_response(&ctx.http, defer_response).await {
                log::error!("Cannot respond to slash command: {e}");
            }

            let result = match command.data.name.as_str() {
                "help" => command::help::HelpCommand::run(&command).await,
                "invite" => {
                    let options = command.data.options();
                    if options.iter().any(|o| o.name == "private") {
                        command::invite::InvitePrivateCommand::run(&command).await
                    } else {
                        command::invite::InvitePublicCommand::run(&command).await
                    }
                }
                "overview" => command::overview::OverviewCommand::run(&command).await,
                _ => {
                    log::error!("Command {} not implemented", command.data.name.as_str());
                    Ok(EditInteractionResponse::new().embed(
                        CreateEmbed::new()
                            .color(Colour::RED)
                            .title("Error")
                            .description("Command not implemented"),
                    ))
                }
            };

            match result {
                Ok(response) => {
                    if let Err(e) = command.edit_response(&ctx.http, response).await {
                        log::error!("Cannot edit slash command response: {e}");
                    }
                }
                Err(e) => {
                    log::error!("Error running command {}: {e}", command.data.name.as_str());
                    if let Err(e) = command
                        .edit_response(
                            &ctx.http,
                            EditInteractionResponse::new().embed(
                                CreateEmbed::new()
                                    .color(Colour::RED)
                                    .title("Error")
                                    .description(if e.is_internal_error() {
                                        format!("Error ({}): {}", e.error_code(), e)
                                    } else {
                                        e.to_string()
                                    }),
                            ),
                        )
                        .await
                    {
                        log::error!("Cannot edit slash command response with error: {e}");
                    }
                }
            }
        }
    }

    async fn guild_create(&self, _ctx: Context, guild: Guild, is_new: Option<bool>) {
        if is_new.unwrap_or(true) {
            log::info!("Joined guild \"{}\" ({})", &guild.name, guild.id.get());

            match acquire_db_connection().await {
                Ok(mut connection) => {
                    let res = diesel::insert_into(guild_specification::table)
                        .values(NewGuildSpecification {
                            guild_id: guild.id.to_string(),
                            guild_name: guild.name.clone(),
                            initialized: false,
                        })
                        .on_conflict_do_nothing()
                        .execute(&mut connection)
                        .await;

                    match res {
                        Ok(rows) => {
                            if rows > 0 {
                                log::info!("Inserted absent guild_specification for guild \"{}\" ({})", &guild.name, guild.id.get());
                            }
                        }
                        Err(e) => log::error!("Failed to create potentially missing guild_specification for guild {}: {e}", guild.id.get())
                    }
                }
                Err(e) => log::error!(
                    "Failed to acquire connection to handle new guild {}: {e}",
                    guild.id.get()
                ),
            }
        }
    }
}

async fn create_guild_specification_if_missing(
    guild_id: GuildId,
    ctx: &Context,
) -> Result<bool, Error> {
    let guild_id_str = guild_id.get().to_string();
    let guild_name = get_name_for_guild(ctx, guild_id).await?;
    let mut connection = acquire_db_connection().await?;
    let existing_specification = guild_specification::table
        .filter(guild_specification::guild_id.eq(&guild_id_str))
        .first::<GuildSpecification>(&mut connection)
        .await
        .optional()?;
    if existing_specification.is_none() {
        let inserted = diesel::insert_into(guild_specification::table)
            .values(NewGuildSpecification {
                guild_id: guild_id_str,
                guild_name: guild_name.clone(),
                initialized: false,
            })
            .on_conflict_do_nothing()
            .execute(&mut connection)
            .await?;
        Ok(inserted > 0)
    } else {
        Ok(false)
    }
}

async fn get_name_for_guild(ctx: &Context, guild_id: GuildId) -> Result<String, Error> {
    if let Some(guild) = ctx.cache.guild(guild_id) {
        return Ok(guild.name.clone()); // Clone immediately
    }

    Ok(guild_id.to_partial_guild(&ctx.http).await?.name.clone())
}
