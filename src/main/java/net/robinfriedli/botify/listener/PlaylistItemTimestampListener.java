package net.robinfriedli.botify.listener;

import java.io.Serializable;
import java.util.Date;

import net.robinfriedli.botify.entities.PlaylistItem;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;

public class PlaylistItemTimestampListener extends ChainableInterceptor {

    public PlaylistItemTimestampListener(Interceptor next) {
        super(next);
    }

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        if (entity instanceof PlaylistItem) {
            Date createdTimestamp = new Date();
            ((PlaylistItem) entity).setCreatedTimestamp(createdTimestamp);
            for (int i = 0; i < propertyNames.length; i++) {
                if ("createdTimestamp".equals(propertyNames[i])) {
                    state[i] = createdTimestamp;
                }
            }
        }
        return next().onSave(entity, id, state, propertyNames, types);
    }

}
