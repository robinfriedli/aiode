package net.robinfriedli.botify.scripting;

import groovy.lang.GroovyShell;
import net.robinfriedli.botify.command.Command;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.discord.MessageService;

public class GroovyVariables {

    public static void addVariables(GroovyShell groovyShell, CommandContext context, Command command, MessageService messageService, SecurityManager securityManager) {
        context.addScriptParameters(groovyShell);
        groovyShell.setVariable("command", command);
        groovyShell.setVariable("messages", messageService);
        groovyShell.setVariable("securityManager", securityManager);
    }

}
