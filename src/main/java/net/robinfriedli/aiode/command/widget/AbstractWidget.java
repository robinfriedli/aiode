package net.robinfriedli.aiode.command.widget;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.discord.DiscordEntity;
import net.robinfriedli.aiode.entities.xml.WidgetContribution;
import net.robinfriedli.aiode.exceptions.UserException;
import org.jetbrains.annotations.Nullable;

/**
 * A widget is an interactive message that can be interacted with by adding reactions. Different reactions trigger
 * different actions that are represented by the reaction's emote.
 */
public abstract class AbstractWidget {

    private final CommandManager commandManager;
    private final WidgetContribution widgetContribution;
    private final WidgetManager widgetManager;
    private final WidgetRegistry widgetRegistry;
    private final DiscordEntity.Guild guild;
    private final DiscordEntity.MessageChannel channel;
    private final Logger logger;

    private DiscordEntity.Message message;
    private volatile boolean messageDeleted;
    private volatile Instant lastInteractionTime = Instant.now();

    protected AbstractWidget(WidgetRegistry widgetRegistry, Guild guild, MessageChannel channel) {
        Aiode aiode = Aiode.get();
        this.commandManager = aiode.getCommandManager();
        this.widgetManager = aiode.getWidgetManager();
        this.widgetRegistry = widgetRegistry;
        this.guild = new DiscordEntity.Guild(guild);
        this.channel = DiscordEntity.MessageChannel.createForMessageChannel(channel);
        widgetContribution = widgetManager.getContributionForWidget(getClass());
        logger = LoggerFactory.getLogger(getClass());
    }

    /**
     * Resets (i.e. re-creates) the message output after the widget action has been executed. The new message needs to be
     * attached to a new instance of this widget.
     *
     * @return the new message embed or null of the message should / has been deleted
     */
    @Nullable
    public abstract MessageEmbed reset();

    public abstract CompletableFuture<Message> prepareInitialMessage();

    public DiscordEntity.Message getMessage() {
        return message;
    }

    public DiscordEntity.Guild getGuild() {
        return guild;
    }

    public DiscordEntity.MessageChannel getChannel() {
        return channel;
    }

    public WidgetContribution getWidgetContribution() {
        return widgetContribution;
    }

    public Instant getLastInteractionTime() {
        return lastInteractionTime;
    }

    public boolean isInactive() {
        Instant now = Instant.now();
        Instant lastInteractionTime = getLastInteractionTime();
        return Duration.between(lastInteractionTime, now).toMinutes() > 60;
    }

    public boolean keepMessageOnDestroy() {
        return widgetContribution.getAttribute("keepMessageOnDestroy").getBool();
    }

    /**
     * Set up the widget so that it is ready to be used. This creates and sends the initial message if necessary,
     * registers the widget in the guild's {@link WidgetRegistry} so that it can be found when receiving reactions and
     * sets up all actions by adding the corresponding reaction emote to the message.
     */
    public void initialise() {
        CompletableFuture<Message> futureMessage = prepareInitialMessage();
        try {
            Message message = futureMessage.get();
            this.message = new DiscordEntity.Message(message);
            widgetRegistry.registerWidget(this);

            try {
                setupActions(message);
            } catch (UserException e) {
                Aiode.get().getMessageService().sendError(e.getMessage(), message.getChannel());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new RuntimeException("Could not initialise widget since setting up the message failed", e);
        }
    }

    /**
     * Adds the reactions representing each available action to the message and prepares all actions
     */
    public void setupActions(Message message) {
        List<List<WidgetManager.WidgetActionDefinition>> actions = widgetManager.getActionsForWidget(getClass());

        GroovyShell predicateEvaluationShell = new GroovyShell();
        predicateEvaluationShell.setVariable("widget", this);

        List<List<Button>> buttons = Lists.newArrayList();
        for (List<WidgetManager.WidgetActionDefinition> actionRow : actions) {
            List<Button> buttonRow = Lists.newArrayList();
            for (WidgetManager.WidgetActionDefinition action : actionRow) {
                if (action.getImplementation().hasAttribute("displayPredicate")) {
                    try {
                        if (!((boolean) predicateEvaluationShell.evaluate(action.getImplementation().getAttribute("displayPredicate").getValue()))) {
                            continue;
                        }
                    } catch (ClassCastException e) {
                        throw new IllegalStateException(String.format("Groovy script in displayPredicate of action contribution %s did not return a boolean", action), e);
                    } catch (Exception e) {
                        throw new IllegalStateException("Exception occurred evaluating displayPredicate of action contribution " + action, e);
                    }
                }
                buttonRow.add(Button.primary(
                    action.getId().getIdentifier(),
                    Emoji.fromUnicode(action.getImplementation().getEmojiUnicode())
                ));
            }
            if (!buttonRow.isEmpty()) {
                buttons.add(buttonRow);
            }
        }

        if (!buttons.isEmpty()) {
            List<ActionRow> actionRows = buttons.stream().map(ActionRow::of).collect(Collectors.toList());
            try {
                message.editMessageComponents(actionRows).queue(s -> {
                }, e -> logger.warn("Failed to edit action row for widget on guild {} with error {}", guild, e.getMessage()));
            } catch (ErrorResponseException e) {
                logger.warn("Failed to edit action row for widget on guild {} with error response {}", guild, e.getErrorCode());
            } catch (Exception e) {
                logger.error("Failed to edit action row for widget on guild " + guild, e);
            }
        }
    }

    public void destroy() {
        widgetRegistry.removeWidget(this);
        if (!messageDeleted && !keepMessageOnDestroy()) {
            Message retrievedMessage = message.retrieve();

            if (retrievedMessage == null) {
                return;
            }

            try {
                try {
                    retrievedMessage.delete().queue();
                } catch (InsufficientPermissionException ignored) {
                    retrievedMessage.editMessageComponents().setComponents().queue();
                }
            } catch (Exception e) {
                logger.warn("Failed to delete message or clear action row for widget", e);
            }
        } else if (!messageDeleted) {
            Message retrievedMessage = message.retrieve();

            if (retrievedMessage == null) {
                return;
            }

            try {
                retrievedMessage.editMessageComponents().setComponents().queue();
            } catch (Exception e) {
                logger.warn("Failed to clear action row for widget", e);
            }
        }
    }

    public void handleButtonInteraction(ButtonInteractionEvent event, CommandContext context) {
        lastInteractionTime = Instant.now();
        widgetManager
            .getWidgetActionDefinitionForComponentId(getClass(), event.getComponentId())
            .map(actionDefinition -> actionDefinition.getImplementation().instantiate(context, this, event, actionDefinition))
            .ifPresent(commandManager::runCommand);
    }

    public WidgetRegistry getWidgetRegistry() {
        return widgetRegistry;
    }

    public boolean isMessageDeleted() {
        return messageDeleted;
    }

    public void setMessageDeleted(boolean messageDeleted) {
        this.messageDeleted = messageDeleted;
    }
}
