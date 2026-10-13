package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class KillSwitchTest {

    @Before
    public void setUp() {
        KillSwitch.resetMemoryState();
    }

    @Test
    public void testKillSwitchDefaultInactive() {
        assertFalse("Kill switch must default to false/inactive", KillSwitch.isActive(null));
    }

    @Test
    public void testKillSwitchActivationAndDeactivation() {
        KillSwitch.activate(null);
        assertTrue("Kill switch must be active after activate()", KillSwitch.isActive(null));

        KillSwitch.deactivate(null);
        assertFalse("Kill switch must be inactive after deactivate()", KillSwitch.isActive(null));
    }

    @Test
    public void testKillSwitchBlocksOrdersInExecutionEngine() {
        ExecutionEngine engine = new ExecutionEngine();
        KillSwitch.activate(null);

        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2640.0, 2670.0, 0.1);
        ExecutionOrder result = engine.executeOrder(order, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.REJECTED, result.status);
        assertTrue(result.rejectionReason.contains("Kill Switch"));
    }
}
