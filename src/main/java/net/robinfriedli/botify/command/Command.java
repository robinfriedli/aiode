package net.robinfriedli.botify.command;

import javax.annotation.Nullable;

import net.robinfriedli.botify.command.widget.AbstractWidgetAction;
import net.robinfriedli.botify.concurrent.CommandExecutionTask;
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
     * @return true to declare the command has failed, this is called after execution and can be used to mark the command
     * as failed even if no exception has been thrown.
     */
    boolean isFailed();

    void setFailed(boolean failed);

    /**
     * @return the {@link CommandContext} for this command execution
     */
    CommandContext getContext();

    /**
     * @return true if the thread executing this command should run in privileged mode, meaning it does not have to wait
     * for other commands to finish if the {@link ThreadExecutionQueue} is full for the current guild.
     */
    boolean isPrivileged();

    /**
     * @return the dedicated thread executing this command, i.e. the thread that is executing the {@link CommandExecutionTask}.
     */
    @Nullable
    default Thread getThread() {
        CommandExecutionTask task = getTask();
        if (task != null) {
            return task.getThread();
        }

        return null;
    }

    /**
     * @return the enqueued task submitted to a {@link ThreadExecutionQueue}, if this command is not executed asynchronously
     * this returns null. This task can be monitored to be notified when it is complete.
     */
    @Nullable
    CommandExecutionTask getTask();

    void setTask(CommandExecutionTask task);

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

    /**
     * @return the permission target for this command used to check access permissions.
     */
    PermissionTarget getPermissionTarget();

    /**
     * Abort command execution, must be called before the command begins execution, commonly from a command interceptor,
     * possibly a scripted interceptor.
     *
     * @return true if the command has already been aborted
     */
    boolean abort();

    /**
     * @return true if command execution has been aborted
     */
    boolean isAborted();

}
