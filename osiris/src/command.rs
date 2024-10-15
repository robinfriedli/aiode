use serenity::all::{CommandInteraction, EditInteractionResponse};

use crate::error::Error;

pub mod help;
pub mod invite;
pub mod overview;

pub trait BotCommand {
    async fn run(interaction: &CommandInteraction) -> Result<EditInteractionResponse, Error>;
}
