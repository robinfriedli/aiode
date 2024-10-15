use dotenvy::dotenv;
use event_handler::DiscordEventHandler;
use lazy_static::lazy_static;
use serenity::all::GatewayIntents;

mod command;
mod db;
mod error;
mod event_handler;
mod glyph_api;
mod model;
mod schema;

lazy_static! {
    pub static ref DISCORD_TOKEN: String = std::env::var("OSIRIS_DISCORD_TOKEN").expect(
        "Missing environment variable OSIRIS_DISCORD_TOKEN must be set to connect to discord"
    );
}

fn main() {
    dotenvy::from_path(
        std::env::current_dir()
            .map(|wd| wd.join(".env.local"))
            .unwrap(),
    )
    .ok();
    dotenvy::from_path(
        std::env::current_dir()
            .map(|wd| wd.join(".env.secret"))
            .unwrap(),
    )
    .ok();
    dotenv().ok();

    lazy_static::initialize(&db::CONNECTION_POOL);

    setup_logger();

    setup_serenity_runtime();
}

#[tokio::main(flavor = "current_thread")]
async fn setup_serenity_runtime() {
    let intents = GatewayIntents::non_privileged();

    let mut client = serenity::Client::builder(&*DISCORD_TOKEN, intents)
        .event_handler(DiscordEventHandler)
        .await
        .expect("Failed to create serenity client");

    if let Err(why) = client.start().await {
        log::error!("An error occurred while starting the serenity client: {why:?}");
    }
}

fn setup_logger() {
    // create logs dir as fern does not appear to handle that itself
    if !std::path::Path::new("logs/").exists() {
        std::fs::create_dir("logs").expect("Failed to create logs/ directory");
    }

    let logging_level = if cfg!(debug_assertions) {
        log::LevelFilter::Debug
    } else {
        log::LevelFilter::Info
    };

    fern::Dispatch::new()
        .format(|out, message, record| {
            out.finish(format_args!(
                "[{}]{}[{}] {}",
                record.level(),
                chrono::Local::now().format("[%Y-%m-%d %H:%M:%S]"),
                record.target(),
                message
            ))
        })
        .level(log::LevelFilter::Info)
        .level_for("osiris", logging_level)
        .level_for("tracing::span", log::LevelFilter::Warn)
        .level_for("serenity::gateway", log::LevelFilter::Warn)
        .level_for("serenity::http", log::LevelFilter::Warn)
        .chain(std::io::stdout())
        .chain(fern::DateBased::new("logs/", "logs_%Y-%m-%d.log"))
        .apply()
        .expect("Failed to set up logging");
}
