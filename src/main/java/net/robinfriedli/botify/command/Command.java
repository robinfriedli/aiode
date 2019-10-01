package net.robinfriedli.botify.command;

import net.robinfriedli.botify.command.widgets.AbstractWidgetAction;
import net.robinfriedli.botify.concurrent.CommandExecutionThread;
import net.robinfriedli.botify.concurrent.ThreadExecutionQueue;

/**
 * Interface for all user triggered actions. See {@link AbstractCommand} for text based commands that are interpreted
 * messages or {@link AbstractWidgetAction} for commands that are widgets that are interacted with by adding reactions to a message.
 */
public interface Command {

    /**
     * The actual logic to run for this command
     *
     * @throws Exception any exception thrown during execution
     */
    void doRun() throws Exception;

    /**
     * Method called after {@link #doRun()} has run successfully. This is the case when no exception is thrown or,
     * depending on the implementation, other criteria, such as {@link #isFailed()} are met.
     */
    void onSuccess();

    /**
     * Method that is executed when {@link #doRun()} fails, either when an exception is thrown or some other custom
     * condition based in implementation. It is important that this method should not throw an exception, otherwise this
     * exception would override the original exception.
     */
    void onFailure();

    /**
     * @return true to define a custom condition where is command is failed
     */
    boolean isFailed();

    /**
     * @return the {@link CommandContext} for this command execution
     */
    CommandContext getContext();

    /**
     * @return true if the thread executing this command should run in privileged mode, meaning it does not have to wait
     * for other commands to finish if the {@link ThreadExecutionQueue} is full for the current guild.
     */
    boolean isPrivileged();

    CommandExecutionThread getThread();

    void setThread(CommandExecutionThread thread);

    /**
     * @return the body of the command, for text based commands this is the input following the command name before
     * parsing.
     */
    String getCommandBody();

    /**
     * @return the identifier, for text based commands this would be the name of the command, of this command. This
     * is relevant to check permissions
     */
    String getIdentifier();

    /**
     * @return a visual representation of this Command. For text based commands this is equal to the raw text entered by
     * the user
     */
    String display();
}
