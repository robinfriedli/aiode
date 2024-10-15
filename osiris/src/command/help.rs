use serenity::all::{CommandInteraction, CreateEmbed, EditInteractionResponse};

use crate::error::Error;

use super::BotCommand;

pub struct HelpCommand;

impl BotCommand for HelpCommand {
    async fn run(_interaction: &CommandInteraction) -> Result<EditInteractionResponse, Error> {
        Ok(EditInteractionResponse::new().embed(
            CreateEmbed::new()
                .title("Help")
                .description("This bot manages invite links for the aiode bot. Use the 'overview' command to view available public and private invite links or use 'invite private' or 'invite public' to get an invite link for a private or public aiode instance.")
        ))
    }
}
