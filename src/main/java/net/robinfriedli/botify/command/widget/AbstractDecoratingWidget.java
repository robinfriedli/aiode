package net.robinfriedli.botify.command.widget;

import java.util.concurrent.CompletableFuture;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;

/**
 * Widget specialisation for widgets that decorate an existing message.
 */
public abstract class AbstractDecoratingWidget extends AbstractWidget {

    // this message won't be stored for long and is just used for the initial setup, it's fine to directly store the jda entity here
    private final Message decoratedMessage;

    public AbstractDecoratingWidget(WidgetRegistry widgetRegistry, Guild guild, Message decoratedMessage) {
        super(widgetRegistry, guild, decoratedMessage.getChannel());
        this.decoratedMessage = decoratedMessage;
    }

    @Override
    public CompletableFuture<Message> prepareInitialMessage() {
        return CompletableFuture.completedFuture(decoratedMessage);
    }
}
