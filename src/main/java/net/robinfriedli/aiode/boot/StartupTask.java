package net.robinfriedli.aiode.boot;

import javax.annotation.Nullable;

import net.dv8tion.jda.api.JDA;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;

/**
 * Interface for tasks that migrate data for updates or ensure the integrity of the database
 */
public interface StartupTask {

    /**
     * The task to run.
     *
     * @param shard the shard this task is executed for, null if runForEachShard is false
     */
    void perform(@Nullable JDA shard) throws Exception;

    default void runTask(@Nullable JDA shard) throws Exception {
        if (shard == null && getContribution().getAttribute("runForEachShard").getBool()) {
            throw new IllegalStateException("Shard is null despite startupTask being marked runForEachShard");
        }
        perform(shard);
    }

    StartupTaskContribution getContribution();

}
