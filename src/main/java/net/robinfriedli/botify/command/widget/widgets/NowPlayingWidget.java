package net.robinfriedli.botify.command.widget.widgets;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.robinfriedli.botify.command.widget.AbstractDecoratingWidget;
import net.robinfriedli.botify.command.widget.WidgetRegistry;

public class NowPlayingWidget extends AbstractDecoratingWidget {

    public NowPlayingWidget(WidgetRegistry widgetRegistry, Guild guild, Message message) {
        super(widgetRegistry, guild, message);
    }

    @Override
    public void reset() {
        setMessageDeleted(true);
        // if a different track is played after using the skip or rewind action, the old "now playing" message will get
        // deleted by the AudioPlayback anyway
    }

    @Override
    public void destroy() {
        getWidgetRegistry().removeWidget(this);
    }
}
