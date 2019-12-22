package net.robinfriedli.botify.persist.tasks;

public interface PersistTask<E> {

    E perform() throws Exception;

}
