package net.robinfriedli.aiode.function;

import java.util.concurrent.Callable;

import net.robinfriedli.aiode.exceptions.CommandRuntimeException;
import net.robinfriedli.aiode.exceptions.UserException;
import net.robinfriedli.aiode.login.Login;
import org.hibernate.Session;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;

/**
 * Invoker to run code either in a hibernate transaction or with Spotify credentials.
 *
 * @deprecated replaced by usages of the JXP {@link net.robinfriedli.jxp.exec.Invoker} using
 * {@link net.robinfriedli.jxp.exec.Invoker.ModeWrapper}s. See {@link HibernateInvoker} or {@link SpotifyInvoker}.
 */
@SuppressWarnings("DuplicatedCode")
@Deprecated
public class Invoker {

    public void invoke(Session session, CheckedRunnable runnable) {
        invoke(session, () -> {
            runnable.doRun();
            return null;
        });
    }

    /**
     * Invoke a callable in a hibernate transaction. There is one Invoker per guild and this method runs synchronised,
     * making sure the same guild can not run this method concurrently. This is to counteract the
     * spamming of a command that uses this method, e.g. spamming the add command concurrently could evade the playlist
     * size limit.
     *
     * @param session  the target hibernate session, individual for each command execution
     * @param callable tho callable to run
     * @param <E>      the return type
     * @return the value the callable returns, often void
     */
    public synchronized <E> E invoke(Session session, Callable<E> callable) {
        boolean isNested = false;
        if (session.getTransaction() == null || !session.getTransaction().isActive()) {
            session.beginTransaction();
        } else {
            isNested = true;
        }
        if (isNested) {
            try {
                return callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CommandRuntimeException(e);
            }
        }
        E retVal;
        try {
            retVal = callable.call();
            session.getTransaction().commit();
        } catch (UserException e) {
            session.getTransaction().rollback();
            throw e;
        } catch (Exception e) {
            session.getTransaction().rollback();
            throw new RuntimeException("Exception in invoked callable. Transaction rolled back.", e);
        }
        return retVal;

    }

    /**
     * Invoke a callable with a Spotify login. Required to access the user's Spotify library for certain commands.
     */
    public <E> E runForUser(Login login, SpotifyApi spotifyApi, Callable<E> callable) throws Exception {
        try {
            spotifyApi.setAccessToken(login.getAccessToken());
            return callable.call();
        } finally {
            spotifyApi.setAccessToken(null);
        }
    }

    /**
     * Invoke a callable with default Spotify API credentials. This is used to query any non user specific data from
     * Spotify.
     */
    public <E> E runWithCredentials(SpotifyApi spotifyApi, Callable<E> callable) throws Exception {
        try {
            ClientCredentials credentials = spotifyApi.clientCredentials().build().execute();
            spotifyApi.setAccessToken(credentials.getAccessToken());

            return callable.call();
        } finally {
            spotifyApi.setAccessToken(null);
        }
    }

}
