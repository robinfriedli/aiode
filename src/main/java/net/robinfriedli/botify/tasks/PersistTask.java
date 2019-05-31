package net.robinfriedli.botify.tasks;

public interface PersistTask<E> {

    E perform() throws Exception;

}
