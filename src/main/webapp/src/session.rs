use std::borrow::Borrow;
use std::str::FromStr;

use seed::*;
use seed::browser::service::fetch::FailReason;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::session::Session::{Anonymous, LoggedIn};

pub const SESSION_STORAGE_KEY: &str = "session-data";
pub const TOKEN_STORAGE_KEY: &str = "token-data";

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Snowflake {
    pub id: u64,
    pub name: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct Client {
    pub user: Snowflake,
    pub guild: Snowflake,
    pub text_channel: Snowflake,
    pub session_id: String,
}

#[derive(Clone, Debug)]
pub enum Session {
    LoggedIn(Client),
    Anonymous,
}

impl Default for Session {
    fn default() -> Self {
        Anonymous
    }
}

impl Session {
    pub fn new(client: Option<Client>) -> Self {
        match client {
            Some(client) => LoggedIn(client),
            None => Anonymous
        }
    }

    pub fn client(&self) -> Option<&Client> {
        match self {
            LoggedIn(client) => Some(client),
            Anonymous => None
        }
    }
}

pub async fn fetch_session<Ms: 'static>(session_id: String, f: fn(Result<Option<Client>, FailReason<String>>) -> Ms) -> Result<Ms, Ms> {
    Request::new(format!("{}{}", "fetch_session/", session_id))
        .method(Method::Get)
        .fetch_string_data(|result| {
            f(result.map(|s| {
                if s.is_empty() {
                    None
                } else {
                    Some(serde_json::from_str::<Client>(s.as_str()).expect("could not deserialize client"))
                }
            }))
        }).await
}

pub async fn generate_token<Ms: 'static>(old_token: Option<String>, f: fn(Result<Uuid, FailReason<String>>) -> Ms) -> Result<Ms, Ms> {
    let url = match old_token {
        Some(token) => format!("{}{}", "generate_token/", token),
        None => String::from("generate_token/")
    };

    Request::new(url)
        .method(Method::Post)
        .fetch_string_data(|result| {
            f(result.map(|s| {
                uuid::Uuid::parse_str(s.as_str()).expect(format!("Could not create uuid from {}", s).as_str())
            }))
        }).await
}

pub async fn check_valid_session<Ms: 'static>(session_id: String, f: fn(Result<bool, FailReason<String>>) -> Ms) -> Result<Ms, Ms> {
    Request::new(format!("{}{}", "session_exists/", session_id))
        .method(Method::Get)
        .fetch_string_data(|result| {
            f(result.map(|s| bool::from_str(s.borrow()).expect("Could not parse result as boolean")))
        }).await
}