package net.robinfriedli.botify.boot;

/**
 * Interface for tasks that ensure the integrity of the XML configuration
 */
public interface StartupTask {

    /**
     * The task to run.
     */
    void perform() throws Exception;

}
