package net.robinfriedli.botify.command.widgets;

import net.dv8tion.jda.api.entities.Message;
import net.robinfriedli.botify.command.AbstractWidget;

public class NowPlayingWidget extends AbstractWidget {

    public NowPlayingWidget(WidgetManager widgetManager, Message message) {
        super(widgetManager, message);
    }

    @Override
    public void reset() {
        setMessageDeleted(true);
        // if a different track is played after using the skip or rewind action, the old "now playing" message will get
        // deleted by the AudioPlayback anyway
    }

    @Override
    public void destroy() {
        getWidgetManager().removeWidget(this);
    }
}
