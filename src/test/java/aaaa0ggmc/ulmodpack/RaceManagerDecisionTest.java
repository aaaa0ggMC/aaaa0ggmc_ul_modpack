package aaaa0ggmc.ulmodpack;

import aaaa0ggmc.ulmodpack.race.RaceManager;
import aaaa0ggmc.ulmodpack.race.RaceManager.TransitionReason;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link RaceManager} 决策表全组合断言（无 Forge 依赖，纯函数）。
 *
 * 依据 docs/dna-recombinator-design.md §3.2：8 个 {@link TransitionReason} ×
 * （压栈 / 打断临时状态）两列，逐行照表断言，防止新增枚举值静默落进错误分支。
 */
public class RaceManagerDecisionTest {

    /** 按 §3.2 决策表逐行抄写的期望：PRESENT = 该列命中。 */
    private static final Set<TransitionReason> EXPECTED_PUSHES_STACK =
            EnumSet.of(TransitionReason.TEMPORARY_PUSH);

    private static final Set<TransitionReason> EXPECTED_INTERRUPTS =
            EnumSet.of(TransitionReason.INFECTION,
                    TransitionReason.EVOLVE,
                    TransitionReason.REVERT_HUMAN);

    @Test
    public void decisionTableMatchesDesignDoc() {
        assertEquals("未新增 reason 时枚举应为 8 个",
                8, TransitionReason.values().length);

        for (TransitionReason reason : TransitionReason.values()) {
            boolean expectedPush = EXPECTED_PUSHES_STACK.contains(reason);
            boolean expectedInterrupt = EXPECTED_INTERRUPTS.contains(reason);

            assertEquals("pushesStack(" + reason + ") 与 §3.2 决策表不符",
                    expectedPush, RaceManager.pushesStack(reason));
            assertEquals("interruptsTemporary(" + reason + ") 与 §3.2 决策表不符",
                    expectedInterrupt, RaceManager.interruptsTemporary(reason));
        }
    }

    /** 压栈与打断互斥：不存在既压栈又打断自身的 reason。 */
    @Test
    public void pushAndInterruptAreMutuallyExclusive() {
        for (TransitionReason reason : TransitionReason.values()) {
            assertFalse("reason " + reason + " 同时压栈并打断临时状态",
                    RaceManager.pushesStack(reason) && RaceManager.interruptsTemporary(reason));
        }
    }

    /** §3.2：ADMIN / VARIANT_APPLY / AGE_SET 决策表行为相同（语义差异只在日志与扩展点）。 */
    @Test
    public void nonInterruptingReasonsShareTheSameRow() {
        for (TransitionReason reason : new TransitionReason[]{
                TransitionReason.ADMIN, TransitionReason.VARIANT_APPLY, TransitionReason.AGE_SET,
                TransitionReason.TEMPORARY_POP}) {
            assertFalse(reason + " 不应压栈", RaceManager.pushesStack(reason));
            assertFalse(reason + " 不应打断临时状态", RaceManager.interruptsTemporary(reason));
        }
    }

    /** 感染 / 进化 / 回撤 必须打断；临时退栈不打断。 */
    @Test
    public void interruptingReasonsAreExactlyTheDocumentedThree() {
        assertTrue(RaceManager.interruptsTemporary(TransitionReason.INFECTION));
        assertTrue(RaceManager.interruptsTemporary(TransitionReason.EVOLVE));
        assertTrue(RaceManager.interruptsTemporary(TransitionReason.REVERT_HUMAN));
        assertFalse(RaceManager.interruptsTemporary(TransitionReason.TEMPORARY_POP));
    }

    /** null 不应落进任何"会动状态"的分支（防御性：Future 调用方传 null 不触发清理）。 */
    @Test
    public void nullReasonIsInert() {
        assertFalse(RaceManager.pushesStack(null));
        assertFalse(RaceManager.interruptsTemporary(null));
    }
}
