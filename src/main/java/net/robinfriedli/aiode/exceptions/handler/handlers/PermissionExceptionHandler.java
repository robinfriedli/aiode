package net.robinfriedli.aiode.exceptions.handler.handlers;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.discord.MessageService;
import net.robinfriedli.aiode.exceptions.handler.ExceptionHandler;
import org.springframework.stereotype.Component;

@Component
public class PermissionExceptionHandler implements ExceptionHandler<PermissionException> {

    @Override
    public Class<PermissionException> getType() {
        return PermissionException.class;
    }

    @Override
    public Result handleException(Throwable uncaughtException, PermissionException exceptionToHandle) {
        if (Aiode.isInitialised()) {
            Aiode aiode = Aiode.get();

            ExecutionContext executionContext = ExecutionContext.Current.get();
            if (executionContext != null) {
                MessageService messageService = aiode.getMessageService();
                Permission permission = exceptionToHandle.getPermission();
                MessageChannel channel = executionContext.getChannel();
                Guild guild = executionContext.getGuild();
                Aiode.LOGGER.warn(String.format("Bot is missing permission %s on guild %s", permission, guild));
                messageService.send("Bot is missing permission: " + permission.getName(), channel);
                return Result.HANDLED;
            }
        }
        return Result.UNHANDLED;
    }
}
