use chrono::{DateTime, Utc};
use lazy_static::lazy_static;
use reqwest::Url;
use serde::Deserialize;
use serenity::all::UserId;

lazy_static! {
    pub static ref GLYPH_ENDPOINT_URL: Url = Url::parse(
        &std::env::var("OSIRIS_GLYPH_ENDPOINT_URL")
            .unwrap_or_else(|_| String::from("https://glyph.robinfriedli.net/glyphbot/"))
    )
    .expect("OSIRIS_GLYPH_ENDPOINT_URL is not a valid URL");
}

#[derive(Deserialize)]
pub struct CheckIsAiodeSupporterResponse {
    pub is_supporter: bool,
    #[allow(dead_code)]
    pub supporter_since: Option<DateTime<Utc>>,
}

pub async fn user_is_aiode_supporter(user_id: UserId) -> bool {
    let url = match GLYPH_ENDPOINT_URL
        .join("is-aiode-supporter/")
        .map(|endpoint| endpoint.join(&user_id.get().to_string()))
    {
        Ok(Ok(url)) => url,
        Err(e) | Ok(Err(e)) => {
            log::error!("Failed to construct URL for is-aiode-supporter endpoint: {e}");
            return false;
        }
    };

    let result = reqwest::get(url).await;
    let response = match result {
        Ok(response) => response,
        Err(e) => {
            log::error!("Failed to send request to is-aiode-supporter endpoint: {e}");
            return false;
        }
    };

    let deserialized_result = response.json::<CheckIsAiodeSupporterResponse>().await;
    let deserialized_response = match deserialized_result {
        Ok(deserialized_response) => deserialized_response,
        Err(e) => {
            log::error!("Failed to deserialize is-aiode-supporter response body to json: {e}");
            return false;
        }
    };

    deserialized_response.is_supporter
}
