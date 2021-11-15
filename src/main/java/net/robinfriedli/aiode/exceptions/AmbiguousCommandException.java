package net.robinfriedli.aiode.exceptions;

import java.util.List;
import java.util.function.Function;

import com.google.common.collect.Lists;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.ClientQuestionEvent;

/**
 * Exception that indicates that several results were found when only one was expected. Used to trigger a
 * {@link ClientQuestionEvent} from outside the {@link AbstractCommand} class during a command execution.
 */
public class AmbiguousCommandException extends UserException {

    private final List<Object> options;
    private final Function<Object, String> displayFunc;

    public AmbiguousCommandException(List<?> options, Function<Object, String> displayFunc) {
        super("Several options found when only one was expected");
        this.options = Lists.newArrayList(options);
        this.displayFunc = displayFunc;
    }

    public List<Object> getOptions() {
        return options;
    }

    public Function<Object, String> getDisplayFunc() {
        return displayFunc;
    }
}
