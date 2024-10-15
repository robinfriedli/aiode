use chrono::{DateTime, Utc};
use diesel::{
    sql_types::{BigInt, Bool, Integer, Nullable, Timestamptz, VarChar},
    Associations, Identifiable, Insertable, Queryable, QueryableByName, Selectable,
};
use serde::Serialize;

use crate::schema::{guild_specification, private_bot_instance};

#[derive(Associations, Identifiable, Queryable, QueryableByName, Selectable, Serialize, Clone)]
#[diesel(table_name = guild_specification)]
#[diesel(primary_key(pk))]
#[diesel(belongs_to(PrivateBotInstance, foreign_key = assigned_private_bot_instance))]
pub struct GuildSpecification {
    #[diesel(sql_type = BigInt)]
    pub pk: i64,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub bot_name: Option<String>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub color: Option<String>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub guild_id: Option<String>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub guild_name: Option<String>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub prefix: Option<String>,
    #[diesel(sql_type = Nullable<Bool>)]
    pub send_playback_notification: Option<bool>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub default_list_source: Option<String>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub default_source: Option<String>,
    #[diesel(sql_type = Nullable<Bool>)]
    pub enable_auto_pause: Option<bool>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub argument_prefix: Option<String>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub default_text_channel_id: Option<String>,
    #[diesel(sql_type = Nullable<Integer>)]
    pub temp_message_timeout: Option<i32>,
    #[diesel(sql_type = Nullable<Bool>)]
    pub enable_scripting: Option<bool>,
    #[diesel(sql_type = Nullable<Integer>)]
    pub default_volume: Option<i32>,
    #[diesel(sql_type = Nullable<Integer>)]
    pub auto_queue_mode: Option<i32>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub version_update_alert_sent: Option<String>,
    #[diesel(sql_type = Nullable<VarChar>)]
    pub assigned_private_bot_instance: Option<String>,
    #[diesel(sql_type = Nullable<Timestamptz>)]
    pub private_bot_assignment_last_heartbeat: Option<DateTime<Utc>>,
    #[diesel(sql_type = Nullable<Bool>)]
    pub initialized: Option<bool>,
}

#[derive(Insertable)]
#[diesel(table_name = guild_specification)]
pub struct NewGuildSpecification {
    pub guild_id: String,
    pub guild_name: String,
    pub initialized: bool,
}

#[derive(Identifiable, Queryable, QueryableByName, Selectable, Serialize, Clone)]
#[diesel(table_name = private_bot_instance)]
#[diesel(primary_key(identifier))]
pub struct PrivateBotInstance {
    #[diesel(sql_type = VarChar)]
    pub identifier: String,
    #[diesel(sql_type = VarChar)]
    pub invite_link: String,
    #[diesel(sql_type = Integer)]
    pub server_limit: i32,
}
