package net.robinfriedli.botify.scripting;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.botify.entities.StoredScript;
import net.robinfriedli.botify.exceptions.Abort;
import net.robinfriedli.botify.exceptions.CommandFailure;
import net.robinfriedli.botify.exceptions.CommandRuntimeException;
import net.robinfriedli.botify.exceptions.ExceptionUtils;

public class SafeGroovyScriptRunner {

    private final CommandContext context;
    private final GroovyShell groovyShell;
    private final GroovyWhitelistInterceptor groovyWhitelistInterceptor;

    public SafeGroovyScriptRunner(CommandContext context, GroovyShell groovyShell, GroovyWhitelistInterceptor groovyWhitelistInterceptor) {
        this.context = context;
        this.groovyShell = groovyShell;
        this.groovyWhitelistInterceptor = groovyWhitelistInterceptor;
    }

    public static String transformScript(String script) {
        // add ThreadInterrupt annotation; this injects interrupt checks into loops and method calls and thus enables
        // interrupting infinite loops
        return String.format("import net.dv8tion.jda.api.EmbedBuilder%1$s" +
            "import java.util.stream.Collectors%1$s" +
            "@groovy.transform.ThreadInterrupt%1$s" +
            "def scriptMethod = {%1$s%2$s%1$s}%1$s" +
            "scriptMethod()", System.lineSeparator(), script);
    }

    public void runScripts(List<StoredScript> scripts, AtomicReference<StoredScript> currentScript, long timeout, TimeUnit timeUnit) throws ExecutionException, TimeoutException {
        CompletableFuture<Object> result = new CompletableFuture<>();
        Thread interceptorExecutionThread = new Thread(() -> {
            CommandContext.Current.set(context);
            groovyWhitelistInterceptor.register();
            scriptExecution(result, () -> {
                for (StoredScript script : scripts) {
                    currentScript.set(script);
                    groovyShell.evaluate(transformScript(script.getScript()));
                }
                result.complete(null);
            });
        });

        interceptorExecutionThread.setName("multiple-scripts-execution-thread-" + context);
        runThreadWithTimeout(interceptorExecutionThread, result, timeout, timeUnit);
    }

    public Object runWithTimeLimit(String script, long timeout, TimeUnit timeUnit) throws ExecutionException, TimeoutException {
        CompletableFuture<Object> result = new CompletableFuture<>();
        Thread scriptExecutionThread = new Thread(() -> {
            CommandContext.Current.set(context);
            groovyWhitelistInterceptor.register();
            scriptExecution(result, () -> result.complete(groovyShell.evaluate(transformScript(script))));
        });

        scriptExecutionThread.setName("script-execution-thread-" + context);
        return runThreadWithTimeout(scriptExecutionThread, result, timeout, timeUnit);
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
            } else if (error instanceof Abort) {
                throw (Abort) error;
            } else {
                EmbedBuilder embedBuilder = ExceptionUtils.buildErrorEmbed(error);
                embedBuilder.setTitle("Error occurred while executing script");
                messageService.sendTemporary(embedBuilder.build(), channel);
                throw new CommandFailure(error);
            }
        }
    }

    private void scriptExecution(CompletableFuture<Object> result, Runnable execution) {
        try {
            execution.run();
        } catch (ThreadDeath e) {
            result.complete(null);
            throw e;
        } catch (Throwable e) {
            // GroovyShell#evaluate does not throw checked exceptions so a catch block for InterruptedException would not
            // compile but the groovy script can throw any kind of exception
            //noinspection ConstantConditions
            if (e instanceof InterruptedException || (e instanceof CommandRuntimeException && e.getCause() instanceof InterruptedException)) {
                result.complete(null);
                return;
            }
            if (e instanceof CommandRuntimeException && e.getCause() instanceof ThreadDeath) {
                result.complete(null);
                throw (ThreadDeath) e.getCause();
            }

            result.completeExceptionally(e);
        }
    }

    @SuppressWarnings("deprecation")
    private Object runThreadWithTimeout(Thread scriptExecutionThread, CompletableFuture<Object> result, long timeout, TimeUnit timeUnit) throws ExecutionException, TimeoutException {
        scriptExecutionThread.start();

        try {
            return result.get(timeout, timeUnit);
        } catch (TimeoutException | InterruptedException e) {
            // first interrupt, in case the thread is holding any open connections or similar
            scriptExecutionThread.interrupt();
            try {
                result.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException | InterruptedException e1) {
                Logger logger = LoggerFactory.getLogger(getClass());
                logger.warn("Forced script execution thread termination after waiting for an additional 10 seconds after sending interrupt signal");
                // then force stop (e.g if script = `while (true) {}` there is not much else to do)
                scriptExecutionThread.interrupt();
                scriptExecutionThread.stop();
                String message = "Script execution was forced to stop after the script did not respond to interrupt signal";
                throw new TimeoutException(e instanceof TimeoutException ? message + " sent after timeout" : message);
            }

            throw new TimeoutException("Script execution timed out");
        }
    }

}
