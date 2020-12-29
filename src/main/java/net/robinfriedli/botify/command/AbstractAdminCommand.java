package net.robinfriedli.botify.command;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;

/**
 * Command extension for administrative commands only users defined as admin user by the ADMIN_USERS property
 * in the settings-private.properties file are allowed to use
 */
public abstract class AbstractAdminCommand extends AbstractCommand {

    public AbstractAdminCommand(CommandContribution commandContribution,
                                CommandContext context,
                                CommandManager commandManager,
                                String commandString,
                                boolean requiresInput,
                                String identifier,
                                String description,
                                Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() throws Exception {
        if (!Botify.get().getSecurityManager().isAdmin(getContext().getUser())) {
            throw new ForbiddenCommandException(getContext().getUser(), getCommandContribution(), "administrator");
        }

        runAdmin();
    }

    @Override
    public boolean isPrivileged() {
        return true;
    }

    public abstract void runAdmin() throws Exception;

    protected void askConfirmation(String description) {
        ClientQuestionEvent question = new ClientQuestionEvent(this);
        question.mapOption("y", true, "Yes");
        question.mapOption("n", false, "No");
        setFailed(true);
        question.ask("Continue?", description);
    }

}
