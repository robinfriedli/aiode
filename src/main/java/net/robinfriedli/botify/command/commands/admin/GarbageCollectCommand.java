package net.robinfriedli.botify.command.commands.admin;

import net.robinfriedli.botify.command.AbstractAdminCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.entities.xml.CommandContribution;

public class GarbageCollectCommand extends AbstractAdminCommand {

    private double memoryBefore;
    private double memoryAfter;

    public GarbageCollectCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void runAdmin() {
        Runtime runtime = Runtime.getRuntime();
        memoryBefore = getUsedMemory(runtime);
        runtime.gc();
        memoryAfter = getUsedMemory(runtime);
    }

    private double getUsedMemory(Runtime runtime) {
        // convert to MB by right shifting by 20 bytes (same as dividing by 2^20)
        double allocatedMemory = runtime.totalMemory() >> 20;
        double allocFreeMemory = runtime.freeMemory() >> 20;
        return allocatedMemory - allocFreeMemory;
    }

    @Override
    public void onSuccess() {
        sendSuccess(String.format("Executed garbage collection. Usage went from %f MB to %f MB.", memoryBefore, memoryAfter));
    }
}
