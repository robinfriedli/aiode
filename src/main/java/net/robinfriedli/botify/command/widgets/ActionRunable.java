package net.robinfriedli.botify.command.widgets;

import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;

public interface ActionRunable {

    void run(GuildMessageReactionAddEvent event) throws Exception;

}
