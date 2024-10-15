use diesel_async::RunQueryDsl;
use serenity::{
    all::{
        Colour, Command, CommandOptionType, Context, CreateCommand, CreateCommandOption,
        CreateEmbed, CreateInteractionResponse, CreateInteractionResponseMessage,
        EditInteractionResponse, EventHandler, Guild, Interaction, Ready,
    },
    async_trait,
};

use crate::{
    command::{self, BotCommand},
    db::acquire_db_connection,
    model::NewGuildSpecification,
    schema::guild_specification,
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
