package net.robinfriedli.botify.command.interceptor.interceptors;

import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.botify.command.interceptor.CommandInterceptor;
import net.robinfriedli.botify.concurrent.CommandExecutionThread;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;

public class CommandMonitoringInterceptor extends AbstractChainableCommandInterceptor {

    public CommandMonitoringInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next) {
        super(contribution, next);
    }

    @Override
    public void performChained(AbstractCommand command) {
        CommandContext context = command.getContext();
        context.registerMonitoring(new Thread(() -> {
            CommandExecutionThread commandExecutionThread = command.getThread();
            try {
                commandExecutionThread.join(5000);
            } catch (InterruptedException e) {
                return;
            }
            if (commandExecutionThread.isAlive()) {
                MessageService messageService = new MessageService();
                messageService.send("Still loading...", context.getChannel());
            }
        }));
        context.startMonitoring();
    }
}
