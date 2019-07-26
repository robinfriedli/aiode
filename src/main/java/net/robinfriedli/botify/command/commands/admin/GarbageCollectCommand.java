package net.robinfriedli.botify.command.commands.admin;

import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class GarbageCollectCommand extends AbstractAdminCommand {

    private double memoryBefore;
    private double memoryAfter;

    public GarbageCollectCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, String identifier, String description) {
        super(commandContribution, context, commandManager, commandString, false, identifier, description);
    }

    @Override
    public void runAdmin() {
        Runtime runtime = Runtime.getRuntime();
        memoryBefore = getUsedMemory(runtime);
        runtime.gc();
        memoryAfter = getUsedMemory(runtime);
    }

    private double getUsedMemory(Runtime runtime) {
        double allocatedMemory = runtime.totalMemory() / Math.pow(1024, 2);
        double allocFreeMemory = runtime.freeMemory() / Math.pow(1024, 2);
        return allocatedMemory - allocFreeMemory;
    }

    @Override
    public void onSuccess() {
        sendSuccess(String.format("Executed garbage collection. Usage went from %s MB to %s MB.", memoryBefore, memoryAfter));
    }
}
