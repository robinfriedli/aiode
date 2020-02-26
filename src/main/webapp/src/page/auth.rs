use std::cmp::PartialEq;

use seed::*;
use seed::browser::service::fetch::FailReason;
use seed::prelude::*;
use seed::virtual_dom::update_el::UpdateEl;
use uuid::Uuid;

use Status::*;

use crate::page::auth::Msg::{Complete, ConnectionResult, TokenRefreshed};
use crate::session;
use crate::session::Client;

#[derive(Default, Debug)]
pub struct Model {
    status: Status
}

#[derive(PartialEq, Debug)]
pub enum Status {
    Idle,
    Connecting,
    ConnectionFailed,
}

impl Default for Status {
    fn default() -> Self {
        Idle
    }
}

#[derive(Clone)]
pub enum Msg {
    Clipboard,
    RefreshToken,
    TokenRefreshed(Result<Uuid, FailReason<String>>),
    ConnectionAttempt(String),
    ConnectionResult(Result<Option<Client>, FailReason<String>>),
    Complete(Client),
}

pub fn update(msg: Msg, model: &mut Model, orders: &mut impl Orders<Msg>) {
    match msg {
        Msg::Clipboard => copy_to_clipboard("uuid_box"),
        Msg::RefreshToken => {
            let storage: storage::Storage = seed::storage::get_storage().expect("Failed to get seed storage");
            let old_token = seed::storage::load_data::<String>(&storage, session::TOKEN_STORAGE_KEY);

            orders.perform_cmd(session::generate_token(old_token, TokenRefreshed));
        }
        Msg::TokenRefreshed(result) => {
            match result {
                Ok(token) => {
                    let storage: storage::Storage = seed::storage::get_storage().expect("Failed to get seed storage");
                    seed::storage::store_data(&storage, session::TOKEN_STORAGE_KEY, &token.to_string());
                }
                Err(err) => {
                    error!("error generating token");
                    error!(err);
                }
            }
        }
        Msg::ConnectionAttempt(token) => {
            model.status = Connecting;
            // make sure submit button changes get rendered
            orders.force_render_now();
            orders.perform_cmd(session::fetch_session(token, ConnectionResult));
        }
        Msg::ConnectionResult(result) => {
            match result {
                Ok(option_client) => {
                    match option_client {
                        Some(client) => {
                            orders.send_msg(Complete(client));
                        }
                        None => {
                            model.status = ConnectionFailed;
                        }
                    }
                }
                Err(err) => {
                    error!("Error while attempting to authenticate");
                    error!(err);
                }
            }
        }
        Msg::Complete(_) => {
            // handled by lib.rs
            unreachable!();
        }
    }
}

pub fn view(model: &Model) -> Node<Msg> {
    let storage: storage::Storage = seed::storage::get_storage().expect("Failed to get seed storage");
    let token_str: String = storage::load_data(&storage, session::TOKEN_STORAGE_KEY).unwrap_or(String::from(""));
    let connection_failed = model.status == ConnectionFailed;
    let connecting = model.status == Connecting;

    let image_style = style! {
        St::Width => "50%";
        St::Height => "50%";
    };

    let text_area_style = style! {
        St::BackgroundColor => "rgba(0,0,0,0)";
        St::Resize => "none";
        St::Border => "solid 1px white";
        St::BorderRadius => "10px";
        St::FontFamily => "Montserrat, Arial";
        St::Width => "400px";
        St::Color => "white";
        St::TextAlign => "center";
        St::PaddingTop => "10px";
        St::Height => "30px";
    };

    let button_style = style! {
        St::BackgroundColor => "rgba(0,0,0,0)";
        St::Height => "40px";
        St::Width => "40px";
        St::Border => "solid 1px white";
        St::BorderRadius => "10px";
        St::MarginLeft => "10px";
    };

    let submit_btn = if connecting {
        button![
            attrs!{At::Disabled => true},
            class!["grey_background round no_border"],
            style!{St::Height => "50px", St::Width => "50px"},
            i![class!["material-icons white"], "done"],
        ]
    } else {
        button![
            class!["green_background round no_border"],
            style!{St::Height => "50px", St::Width => "50px"},
            i![class!["material-icons white"], "done"],
            simple_ev(Ev::Click, Msg::ConnectionAttempt(token_str.clone()))
        ]
    };

    div![class!["center"],
        img![image_style, attrs! {At::Src => "img/botify-logo-wide-transparent.png"}],
        h1![class!["standard_text white"], "Your token"],
        div![
            style!{St::Width => "510px", St::Height=> "50px", St::Margin => "0 auto"},
            div![class!["left center"], textarea![text_area_style, token_str, attrs!{At::ReadOnly => true, At::Id => "uuid_box"}]],
            div![class!["right center"],
                button![
                    &button_style,
                    i![class!["material-icons white"], "file_copy"],
                    simple_ev(Ev::Click, Msg::Clipboard)
                ],
                button![
                    &button_style,
                    i![class!["material-icons white"], "refresh"],
                    simple_ev(Ev::Click, Msg::RefreshToken)
                ]
            ]
        ],
        br![],
        div![
            style!{St::Clear => "both"},
            submit_btn,
            p![class!["standard_text white"], raw!("Copy the token then go to the guild and text channel you want to connect to and then use the connect command. <br/>DM the bot the token when prompted and then press the button to continue.")],
            if connection_failed {
                p![class!["standard_text red"], "Could not authenticate. Make sure the bot confirmed that a session had been prepared."]
            } else {
                empty![]
            }
        ]
    ]
}

#[wasm_bindgen]
extern "C" {
    fn copy_to_clipboard(element_id: &str);
}