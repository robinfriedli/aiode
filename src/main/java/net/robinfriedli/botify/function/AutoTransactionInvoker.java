package net.robinfriedli.botify.function;

import java.util.function.Function;

import net.robinfriedli.botify.command.CommandContext;
import org.hibernate.Session;

/**
 * Invokes a function with a session, automatically managing a transaction depending on CommandContext
 */
public class AutoTransactionInvoker implements FunctionInvoker<Session> {

    private final Session session;

    public AutoTransactionInvoker(Session session) {
        this.session = session;
    }

    public static AutoTransactionInvoker create(Session session) {
        return new AutoTransactionInvoker(session);
    }

    @Override
    public <V> V invoke(Function<Session, V> function) {
        boolean commitRequired = false;
        if (!CommandContext.Current.isSet()) {
            if (session.getTransaction() == null || !session.getTransaction().isActive()) {
                session.beginTransaction();
                commitRequired = true;
            }
        }
        try {
            return function.apply(session);
        } catch (Throwable e) {
            if (commitRequired) {
                session.getTransaction().rollback();
                // make sure this transaction is not committed in the finally block, which would throw an exception that
                // overrides the current exception
                commitRequired = false;
            }
            throw e;
        } finally {
            if (commitRequired) {
                session.getTransaction().commit();
            }
        }
    }
}
