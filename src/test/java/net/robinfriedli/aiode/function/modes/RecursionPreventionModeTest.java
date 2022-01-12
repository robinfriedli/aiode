package net.robinfriedli.aiode.function.modes;

import org.testng.annotations.*;

import net.robinfriedli.exec.BaseInvoker;
import net.robinfriedli.exec.Mode;

public class RecursionPreventionModeTest {

    @Test
    public void testNoRecurse() {
        RecursionPreventionMode recursionPreventionMode = new RecursionPreventionMode("test");

        new BaseInvoker().invoke(Mode.create().with(recursionPreventionMode), this::testNoRecurse);
    }

}
