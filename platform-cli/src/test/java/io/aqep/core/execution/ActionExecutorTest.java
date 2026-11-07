package io.aqep.core.execution;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Unit tests for ActionExecutor
 */
public class ActionExecutorTest {

    @Test
    public void testActionExecutorInitialization() {
        assertNotNull(new ActionExecutor());
    }

    @Test
    public void testSimpleExample() {
        assertEquals(1, 1);
    }
}
