use seed::{*, prelude::*};

use crate::session::Client;

pub type Msg = i32;

pub struct Model;

pub fn view(model: &Model, client: &Client) -> Node<Msg> {
    let user = &client.user;
    let guild = &client.guild;
    let text_channel = &client.text_channel;

    div![
        h1![class!["standard_text white"],
            format!("Welcome {}", client.user.name)
        ],
        table![class!["standard_text standard_table white"],
            tr![
                td!["User:"],
                td![format!("{}", user.name)]
            ],
            tr![
                td!["Guild:"],
                td![format!("{}", guild.name)]
            ],
            tr![
                td!["Channel:"],
                td![format!("{}", text_channel.name)]
            ]
        ]
    ]
}