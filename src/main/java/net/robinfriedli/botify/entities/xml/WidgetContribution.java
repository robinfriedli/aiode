package net.robinfriedli.botify.entities.xml;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.widget.AbstractWidget;
import net.robinfriedli.botify.command.widget.AbstractWidgetAction;
import net.robinfriedli.botify.command.widget.WidgetManager;
import net.robinfriedli.jxp.collections.NodeList;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class WidgetContribution extends GenericClassContribution<AbstractWidget> {

    // invoked by JXP
    @SuppressWarnings("unused")
    public WidgetContribution(Element element, NodeList subElements, Context context) {
        super(element, subElements, context);
    }

    @Nullable
    @Override
    public String getId() {
        return getAttribute("implementation").getValue();
    }

    public boolean shouldClearReactionOnReset() {
        return getAttribute("clearReactionOnReset").getBool();
    }

    public static class WidgetActionContribution extends GenericClassContribution<AbstractWidgetAction> {

        // invoked by JXP
        @SuppressWarnings("unused")
        public WidgetActionContribution(Element element, NodeList subElements, Context context) {
            super(element, subElements, context);
        }

        @Nullable
        @Override
        public String getId() {
            return getAttribute("implementation").getValue();
        }

        public String getIdentifier() {
            return getAttribute("identifier").getValue();
        }

        public String getEmojiUnicode() {
            return getAttribute("emojiUnicode").getValue();
        }

        public AbstractWidgetAction instantiate(CommandContext context, AbstractWidget widget, GuildMessageReactionAddEvent event, WidgetManager.WidgetActionDefinition definition) {
            Class<? extends AbstractWidgetAction> implementationClass = getImplementationClass();
            Constructor<? extends AbstractWidgetAction> constructor;
            try {
                constructor = implementationClass.getConstructor(
                    String.class,
                    String.class,
                    Boolean.TYPE,
                    CommandContext.class,
                    AbstractWidget.class,
                    GuildMessageReactionAddEvent.class,
                    WidgetManager.WidgetActionDefinition.class
                );
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Widget " + implementationClass + " does not have the appropriate constructor", e);
            }

            String identifier = getIdentifier();
            String emojiUnicode = getAttribute("emojiUnicode").getValue();
            boolean resetRequired = getAttribute("resetRequired").getBool();
            try {
                return constructor.newInstance(
                    identifier,
                    emojiUnicode,
                    resetRequired,
                    context,
                    widget,
                    event,
                    definition
                );
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Cannot instantiate " + constructor, e);
            }
        }

    }

}
