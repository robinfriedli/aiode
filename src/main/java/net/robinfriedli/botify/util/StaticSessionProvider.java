package net.robinfriedli.botify.util;

import java.util.function.Consumer;
import java.util.function.Function;

import net.robinfriedli.botify.command.CommandContext;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class StaticSessionProvider {

    public static SessionFactory sessionFactory;

    public static Session provide() {
        if (sessionFactory == null) {
            throw new IllegalStateException("SessionFactory has not been set up yet");
        }

        if (CommandContext.Current.isSet()) {
            return CommandContext.Current.require().getSession();
        }

        return sessionFactory.openSession();
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            throw new IllegalStateException("SessionFactory has not been set up yet");
        }

        return sessionFactory;
    }

    public static void invokeWithSession(Consumer<Session> consumer) {
        invokeWithSession(session -> {
            consumer.accept(session);
            return null;
        });
    }

    public static <E> E invokeWithSession(Function<Session, E> function) {
        boolean closeAfter = !CommandContext.Current.isSet();
        Session session = provide();
        try {
            return function.apply(session);
        } finally {
            if (closeAfter) {
                session.close();
            }
        }
    }

}
