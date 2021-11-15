package net.robinfriedli.aiode.persist.tasks;

public interface PersistTask<E> {

    E perform() throws Exception;

}
