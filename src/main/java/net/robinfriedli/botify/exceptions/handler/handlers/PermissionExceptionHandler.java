package net.robinfriedli.botify.exceptions.handler.handlers;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.exceptions.handler.ExceptionHandler;
import org.springframework.stereotype.Component;

@Component
public class PermissionExceptionHandler implements ExceptionHandler<PermissionException> {

    @Override
    public Class<PermissionException> getType() {
        return PermissionException.class;
    }

    @Override
    public Result handleException(Throwable uncaughtException, PermissionException exceptionToHandle) {
        if (Botify.isInitialised()) {
            Botify botify = Botify.get();

            ExecutionContext executionContext = ExecutionContext.Current.get();
            if (executionContext != null) {
                MessageService messageService = botify.getMessageService();
                Permission permission = exceptionToHandle.getPermission();
                TextChannel channel = executionContext.getChannel();
                Guild guild = executionContext.getGuild();
                Botify.LOGGER.warn(String.format("Bot is missing permission %s on guild %s", permission, guild));
                messageService.send("Bot is missing permission: " + permission.getName(), channel);
                return Result.HANDLED;
            }
        }
        return Result.UNHANDLED;
    }
}
