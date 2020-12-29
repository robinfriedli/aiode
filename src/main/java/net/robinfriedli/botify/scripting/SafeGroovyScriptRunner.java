package net.robinfriedli.botify.scripting;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.boot.ShutdownableExecutorService;
import net.robinfriedli.botify.boot.configurations.GroovySandboxComponent;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.concurrent.ForkTaskTreadPool;
import net.robinfriedli.botify.concurrent.LoggingThreadFactory;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.botify.entities.StoredScript;
import net.robinfriedli.botify.exceptions.CommandFailure;
import net.robinfriedli.botify.exceptions.ExceptionUtils;
import net.robinfriedli.threadpool.ThreadPool;

public class SafeGroovyScriptRunner {

    private static final ForkTaskTreadPool GLOBAL_POOL = new ForkTaskTreadPool(
        ThreadPool.Builder.create()
            .setCoreSize(3)
            .setMaxSize(Integer.MAX_VALUE)
            .setKeepAlive(60L, TimeUnit.SECONDS)
            .setWorkQueue(new SynchronousQueue<>())
            .setThreadFactory(new LoggingThreadFactory("script-execution-pool"))
            .build()
    );

    static {
        Botify.SHUTDOWNABLES.add(new ShutdownableExecutorService(GLOBAL_POOL));
    }

    private final CommandContext context;
    private final GroovySandboxComponent groovySandboxComponent;
    private final GroovyVariableManager groovyVariableManager;
    private final GroovyWhitelistManager groovyWhitelistManager;
    private final SecurityManager securityManager;
    private final boolean isPrivileged;

    public SafeGroovyScriptRunner(
        CommandContext context,
        GroovySandboxComponent groovySandboxComponent,
        GroovyVariableManager groovyVariableManager,
        SecurityManager securityManager,
        boolean isPrivileged
    ) {
        this.context = context;
        this.groovySandboxComponent = groovySandboxComponent;
        this.groovyWhitelistManager = groovySandboxComponent.getGroovyWhitelistManager();
        this.groovyVariableManager = groovyVariableManager;
        this.securityManager = securityManager;
        this.isPrivileged = isPrivileged;
    }

    public void runScripts(List<StoredScript> scripts, AtomicReference<StoredScript> currentScript, long timeout, TimeUnit timeUnit) throws ExecutionException, TimeoutException {
        GroovyShell groovyShell = createShell();
        Future<Object> result = scriptExecution(() -> {
            for (StoredScript script : scripts) {
                currentScript.set(script);
                groovyShell.evaluate(script.getScript());
            }
            return null;
        });

        runScriptWithTimeout(result, timeout, timeUnit);
    }

    public Object runWithTimeLimit(String script, long timeout, TimeUnit timeUnit) throws ExecutionException, TimeoutException {
        GroovyShell groovyShell = createShell();
        Future<Object> result = scriptExecution(() -> groovyShell.evaluate(script));

        return runScriptWithTimeout(result, timeout, timeUnit);
    }

    public void runAndSendResult(String script, long timeout, TimeUnit timeUnit) {
        MessageService messageService = Botify.get().getMessageService();
        MessageChannel channel = context.getChannel();
        try {
            Object result = runWithTimeLimit(script, timeout, timeUnit);
            if (result != null) {
                String resultString = result.toString();
                if (resultString.length() < 1000) {
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setTitle("Output");
                    embedBuilder.setDescription("```" + resultString + "```");
                    messageService.send(embedBuilder, channel);
                } else {
                    EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("Output too long, attaching as file");
                    embedBuilder.setColor(ColorSchemeProperty.getColor());
                    byte[] bytes = resultString.getBytes();

                    if (bytes.length > 1000000) {
                        messageService.sendError("Output too long", channel);
                        return;
                    }

                    ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                    messageService.executeMessageAction(channel, c -> c.sendMessage(embedBuilder.build()).addFile(inputStream, "output.txt"));
                }
            }
        } catch (TimeoutException e) {
            messageService.sendError(e.getMessage(), channel);
        } catch (ExecutionException e) {
            Throwable error = e.getCause() != null ? e.getCause() : e;

            if (error instanceof SecurityException) {
                messageService.sendError(error.getMessage(), channel);
                throw new CommandFailure(error);
            } else if (!(error instanceof CommandFailure)) {
                EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(error);
                embedBuilder.setTitle("Error occurred while executing script");
                messageService.sendTemporary(embedBuilder.build(), channel);
                throw new CommandFailure(error);
            }
        }
    }

    private <T> Future<T> scriptExecution(Callable<T> execution) {
        return GLOBAL_POOL.submit(() -> {
            try {
                return execution.call();
            } finally {
                groovyWhitelistManager.resetInvocationCounts();
            }
        });
    }

    private <T> T runScriptWithTimeout(Future<T> result, long timeout, TimeUnit timeUnit) throws ExecutionException, TimeoutException {
        try {
            if (isPrivileged) {
                return result.get();
            } else {
                return result.get(timeout, timeUnit);
            }
        } catch (CancellationException e) {
            return null;
        } catch (InterruptedException e) {
            result.cancel(true);
            return null;
        } catch (TimeoutException e) {
            result.cancel(true);
            throw new TimeoutException("Script execution timed out");
        }
    }

    private GroovyShell createShell() {
        GroovyShell groovyShell;

        if (isPrivileged) {
            User user = context.getUser();
            if (!securityManager.isAdmin(user)) {
                throw new SecurityException(String.format("Cannot set up privileged shell for user %s, only allowed for admin users.", user.getAsMention()));
            }
            groovyShell = new GroovyShell(groovySandboxComponent.getPrivilegedCompilerConfiguration());
        } else {
            groovyShell = new GroovyShell(groovySandboxComponent.getCompilerConfiguration());
        }

        groovyVariableManager.prepareShell(groovyShell);

        return groovyShell;
    }

}
