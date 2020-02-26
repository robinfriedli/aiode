use seed::{*, prelude::*};
use seed::browser::service::fetch::FailReason;
use uuid::Uuid;

use crate::CurrentModel::{Auth, Home, Start};
use crate::Msg::{AuthMsg, HomeMsg, SessionChecked, TokenChecked, TokenFetched};
use crate::page::auth::Msg::Complete;
use crate::session::Client;
use crate::Status::{AuthenticationRequired, Loading, SessionPrepared};

mod session;
mod page;

#[derive(Default)]
struct Model {
    status: Status,
    current_model: CurrentModel,
}

enum Status {
    AuthenticationRequired(Uuid),
    SessionPrepared(Client),
    Loading,
}

enum CurrentModel {
    Auth(page::auth::Model),
    Home(page::home::Model),
    Start,
}

pub enum Msg {
    SessionChecked(Result<bool, FailReason<String>>),
    TokenChecked(Result<Option<Client>, FailReason<String>>),
    TokenFetched(Result<Uuid, FailReason<String>>),
    AuthMsg(page::auth::Msg),
    HomeMsg(page::home::Msg),
}

impl Default for Status {
    fn default() -> Self {
        Loading
    }
}

impl Default for CurrentModel {
    fn default() -> Self {
        Start
    }
}

fn update(msg: Msg, model: &mut Model, orders: &mut impl Orders<Msg>) {
    let storage: storage::Storage = seed::storage::get_storage().expect("Failed to get seed storage");
    match msg {
        SessionChecked(result) => {
            match result {
                Ok(valid_session) => {
                    if valid_session {
                        let client = storage::load_data::<Client>(&storage, session::SESSION_STORAGE_KEY).expect("Could not load stored Client despite the session check returning true");

                        model.status = SessionPrepared(client);
                        model.current_model = Home(page::home::Model);
                    } else {
                        check_stored_token(&storage, orders);
                    }
                }
                Err(err) => {
                    error!("Error while checking stored session");
                    error!(err);
                    check_stored_token(&storage, orders);
                }
            }
        }
        TokenChecked(result) => {
            match result {
                Ok(option_client) => match option_client {
                    Some(client) => {
                        storage::store_data(&storage, session::SESSION_STORAGE_KEY, &client);

                        model.status = SessionPrepared(client);
                        model.current_model = Home(page::home::Model);
                    }
                    None => {
                        let old_token = seed::storage::load_data::<String>(&storage, session::TOKEN_STORAGE_KEY);
                        orders.perform_cmd(session::generate_token(old_token, TokenFetched));
                    }
                },
                Err(err) => {
                    error!("Error while checking stored token");
                    error!(err);
                    let old_token = seed::storage::load_data::<String>(&storage, session::TOKEN_STORAGE_KEY);
                    orders.perform_cmd(session::generate_token(old_token, TokenFetched));
                }
            }
        }
        TokenFetched(result) => {
            match result {
                Ok(token) => {
                    let storage: storage::Storage = seed::storage::get_storage().expect("Failed to get seed storage");
                    seed::storage::store_data(&storage, session::TOKEN_STORAGE_KEY, &token.to_string());
                    model.status = AuthenticationRequired(token);
                    model.current_model = Auth(page::auth::Model::default());
                }
                Err(err) => {
                    error!("Error while fetching token");
                    error!(err);
                }
            }
        }
        HomeMsg(home_msg) => {}
        AuthMsg(auth_msg) => {
            if let Complete(client) = auth_msg {
                storage::store_data(&storage, session::SESSION_STORAGE_KEY, &client);

                model.status = SessionPrepared(client);
                model.current_model = Home(page::home::Model);
            } else if let Auth(ref mut model) = model.current_model {
                page::auth::update(auth_msg, model, &mut orders.proxy(AuthMsg));
            }
        }
    }
}

fn after_mount(_url: Url, orders: &mut impl Orders<Msg>) -> AfterMount<Model> {
    let storage: storage::Storage = seed::storage::get_storage().expect("Failed to get seed storage");
    match seed::storage::load_data::<Client>(&storage, session::SESSION_STORAGE_KEY) {
        Some(client) => {
            let session_id = String::from(client.session_id);

            orders.perform_cmd(session::check_valid_session(session_id, SessionChecked));
        }
        None => {
            orders.send_msg(SessionChecked(Ok(false)));
        }
    };

    AfterMount::default()
}

fn view(model: &Model) -> Node<Msg> {
    match &model.status {
        AuthenticationRequired(_) => {
            match &model.current_model {
                Auth(auth_model) => page::auth::view(auth_model).map_msg(AuthMsg),
                _ => page::auth::view(&page::auth::Model::default()).map_msg(AuthMsg)
            }
        }
        SessionPrepared(client) => page::home::view(&page::home::Model, client).map_msg(HomeMsg),
        Loading => {
            div![h1!["Loading"]]
        }
    }
}

fn check_stored_token(storage: &storage::Storage, orders: &mut impl Orders<Msg>) {
    match storage::load_data::<String>(storage, session::TOKEN_STORAGE_KEY) {
        Some(token) => {
            orders.perform_cmd(session::fetch_session(token, TokenChecked));
        }
        None => {
            orders.perform_cmd(session::generate_token(None, TokenFetched));
        }
    }
}

#[wasm_bindgen(start)]
pub fn render() {
    App::builder(update, view).after_mount(after_mount).build_and_start();
}