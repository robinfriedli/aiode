package net.robinfriedli.botify.util;

import java.util.Arrays;
import java.util.Optional;

public class ParameterContainer {

    private final Object[] objects;

    public ParameterContainer(Object... objects) {
        this.objects = objects;
    }

    @SuppressWarnings("unchecked")
    public <E> E get(Class<E> type) {
        Optional<Object> optionalObject = Arrays.stream(objects).filter(type::isInstance).findAny();
        return (E) optionalObject.orElse(null);
    }

}
