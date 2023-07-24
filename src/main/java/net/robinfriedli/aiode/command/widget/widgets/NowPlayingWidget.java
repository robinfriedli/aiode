package net.robinfriedli.aiode.command.widget.widgets;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.robinfriedli.aiode.command.widget.AbstractDecoratingWidget;
import net.robinfriedli.aiode.command.widget.WidgetRegistry;

public class NowPlayingWidget extends AbstractDecoratingWidget {

    public NowPlayingWidget(WidgetRegistry widgetRegistry, Guild guild, Message message) {
        super(widgetRegistry, guild, message);
    }

    @Override
    public MessageEmbed reset() {
        setMessageDeleted(true);
        // if a different track is played after using the skip or rewind action, the old "now playing" message will get
        // deleted by the AudioPlayback anyway
        return null;
    }

    @Override
    public void destroy() {
        getWidgetRegistry().removeWidget(this);
    }
}
