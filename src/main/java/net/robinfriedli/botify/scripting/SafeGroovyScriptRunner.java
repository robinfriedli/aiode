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
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.concurrent.ExecutionContext;
import net.robinfriedli.botify.concurrent.ForkTaskTreadPool;
import net.robinfriedli.botify.concurrent.LoggingThreadFactory;
import net.robinfriedli.botify.discord.MessageService;
import net.robinfriedli.botify.discord.property.properties.ColorSchemeProperty;
import net.robinfriedli.botify.entities.StoredScript;
import net.robinfriedli.botify.exceptions.CommandFailure;
import net.robinfriedli.botify.exceptions.ExceptionUtils;
import net.robinfriedli.threadpool.ThreadPool;

/**
 * Class that provides safe execution of untrusted groovy scripts by setting up a sandboxed {@link GroovyShell} with compilation
 * customizers that check method and property access, transforms method invocations for which an invocation limit is set
 * to check and increment the invocation counter, adds a total limit of loop iterations and method invocations and enables
 * running scripts with a time limit by injecting interrupt checks into the script. The security sandbox can be disabled
 * using the isPrivileged constructor parameter, reducing the number of applied compilation customizers and ignoring time
 * limits while still applying the ImportCustomizer and ThreadInterrupt customizer to enable optionally interrupting the
 * script manually using the abort command. Whitelisted methods and properties are configured in the groovyWhitelist.xml
 * file.
 */
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

    private final ExecutionContext context;
    private final GroovySandboxComponent groovySandboxComponent;
    private final GroovyVariableManager groovyVariableManager;
    private final GroovyWhitelistManager groovyWhitelistManager;
    private final SecurityManager securityManager;
    private final boolean isPrivileged;

    public SafeGroovyScriptRunner(
        ExecutionContext context,
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

    /**
     * Run all provided scripts sequentially in the same thread. This counts as one single script execution, thus all scripts
     * share the same method invocation limits and run with one time limit. This method is mainly used by the ScriptCommandInterceptor
     * to run all interceptors or all finalizers. Just like {@link #evaluateScript(String, long, TimeUnit)} this applies
     * all security checks and expression transformation if isPrivileged is false.
     *
     * @param scripts       a list of {@link StoredScript} entities representing the scripts to run
     * @param currentScript a reference pointing to the script that is currently being executed, can be used to reference
     *                      the last executed script if execution fails or execution runs into a timeout
     * @param timeout       the time delta in which all provided scripts have to run, ignored if isPrivileged is true
     * @param timeUnit      the time unit of the timeout
     * @throws ExecutionException if a script fails due to an exception
     * @throws TimeoutException   if not all scripts finish within the given time limit
     */
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

    /**
     * Evaluate the script by running it using the groovy shell set up for this instance. If isPrivileged is false
     * static compilation, the type checking extensions to check whitelisted method invocations and property access and
     * other compilation customizers are applied and the script runs under a timeout.
     *
     * @param script   the groovy code to run
     * @param timeout  the timeout, ignored if isPrivileged is true
     * @param timeUnit the time unit for the timout parameter
     * @return the object returned by the script
     * @throws ExecutionException if script execution throws an exection
     * @throws TimeoutException   if the script runs into a timeout
     */
    public Object evaluateScript(String script, long timeout, TimeUnit timeUnit) throws ExecutionException, TimeoutException {
        GroovyShell groovyShell = createShell();
        Future<Object> result = scriptExecution(() -> groovyShell.evaluate(script));

        return runScriptWithTimeout(result, timeout, timeUnit);
    }

    /**
     * Run a groovy script and send its result, returned object or thrown exception, to the channel of the {@link ExecutionContext}
     * this instance was set up with. This method calls {@link #evaluateScript(String, long, TimeUnit)} and handles the
     * result by sending the string representation of the result or an error message should an error occur. If the script
     * returns null this method sends nothing.
     *
     * @param script   the groovy code to run
     * @param timeout  the timeout, ignored if isPrivileged is true
     * @param timeUnit the time unit for the timout parameter
     */
    public void runAndSendResult(String script, long timeout, TimeUnit timeUnit) {
        MessageService messageService = Botify.get().getMessageService();
        MessageChannel channel = context.getChannel();
        try {
            Object result = evaluateScript(script, timeout, timeUnit);
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
