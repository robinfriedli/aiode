package net.robinfriedli.botify.command.commands.customisation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

public class RenameCommand extends AbstractCommand {

    private boolean couldChangeNickname;

    public RenameCommand(CommandContribution commandContribution, CommandContext commandContext, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, commandContext, commandManager, commandString, true, identifier, description, Category.CUSTOMISATION);
    }

    @Override
    public void doRun() {
        String name = getCommandInput();
        CommandContext context = getContext();
        Guild guild = context.getGuild();

        if (name.length() < 1 || name.length() > 32) {
            throw new InvalidCommandException("Length should be 1 - 32 characters");
        }

        // create enclosing transaction that is blocked until the nickname is set
        invoke(() -> {
            context.getGuildContext().setBotName(name);
            try {
                // use complete() in this case to make the transaction wait fo the nickname change to complete
                guild.getSelfMember().modifyNickname(name).complete();
                couldChangeNickname = true;
            } catch (InsufficientPermissionException ignored) {
                couldChangeNickname = false;
            }
        });
    }

    @Override
    public void onSuccess() {
        String name = getContext().getGuildContext().getSpecification().getBotName();
        if (couldChangeNickname) {
            // notification sent by GuildPropertyInterceptor
        } else {
            sendError("I do not have permission to change my nickname, but you can still call me " + name);
        }
    }

}
