package net.robinfriedli.botify.command;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.ForbiddenCommandException;

/**
 * Commands that manage the application only administrators defined in the settings.properties file can use
 */
public abstract class AbstractAdminCommand extends AbstractCommand {

    public AbstractAdminCommand(CommandContribution commandContribution,
                                CommandContext context,
                                CommandManager commandManager,
                                String commandString,
                                boolean requiresInput,
                                String identifier,
                                String description) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, Category.ADMIN);
    }

    @Override
    public void doRun() throws Exception {
        if (!Botify.get().getSecurityManager().isAdmin(getContext().getUser())) {
            throw new ForbiddenCommandException(getContext().getUser(), getIdentifier(), "administrator");
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
