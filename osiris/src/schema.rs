diesel::table! {
    guild_specification (pk) {
        pk -> BigInt,
        bot_name -> Nullable<VarChar>,
        color -> Nullable<VarChar>,
        guild_id -> Nullable<VarChar>,
        guild_name -> Nullable<VarChar>,
        prefix -> Nullable<VarChar>,
        send_playback_notification -> Nullable<Bool>,
        default_list_source -> Nullable<VarChar>,
        default_source -> Nullable<VarChar>,
        enable_auto_pause -> Nullable<Bool>,
        argument_prefix -> Nullable<VarChar>,
        default_text_channel_id -> Nullable<VarChar>,
        temp_message_timeout -> Nullable<Int4>,
        enable_scripting -> Nullable<Bool>,
        default_volume -> Nullable<Int4>,
        auto_queue_mode -> Nullable<Int4>,
        version_update_alert_sent -> Nullable<VarChar>,
        assigned_private_bot_instance -> Nullable<VarChar>,
        private_bot_assignment_last_heartbeat -> Nullable<Timestamptz>,
        initialized -> Nullable<Bool>
    }
}

diesel::table! {
    private_bot_instance (identifier) {
        identifier -> VarChar,
        invite_link -> VarChar,
        server_limit -> Int4
    }
}

diesel::joinable!(guild_specification -> private_bot_instance (assigned_private_bot_instance));

diesel::allow_tables_to_appear_in_same_query!(guild_specification, private_bot_instance,);
