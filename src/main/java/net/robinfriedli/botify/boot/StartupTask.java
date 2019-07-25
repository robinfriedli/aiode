package net.robinfriedli.botify.boot;

/**
 * Interface for tasks that migrate data for updates or ensure the integrity of the database
 */
public interface StartupTask {

    /**
     * The task to run.
     */
    void perform() throws Exception;

}
