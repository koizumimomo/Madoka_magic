package io.madoka.magic.spell;

/**
 * 伤害结算的上下文标记。
 *
 * <p>有些技能需要<b>无视阵营减免</b>（例如赎罪之刃的"处刑"：明确要跨阵营满伤结算），
 * 但阵营减免是在 {@code TaintEvents#onLivingHurt} 里统一乘的。
 * 直接给某个技能写 if-else 会让阵营层和技能层耦合，
 * 所以这里提供一个"结算期间的可查询标记"：</p>
 *
 * <pre>
 * DamageContext.runBypassingSameFaction(() -> target.hurt(source, damage));
 * </pre>
 *
 * <p>{@code TaintEvents} 在乘同阵营减免前查 {@link #isBypassingSameFaction()}，
 * 为 true 时跳过这一层。这正是 {@code docs/框架层说明.md §3.2} 推荐的扩展方式。</p>
 *
 * <p><b>注意</b>：只允许在服务端主线程的<b>同步伤害结算</b>里用（{@code hurt()} 会同步触发
 * {@code LivingHurtEvent}）。标记用深度计数，嵌套调用也能正确恢复。</p>
 */
public final class DamageContext {

    private static int bypassSameFactionDepth;

    private DamageContext() {
    }

    /** 在这段代码里结算的伤害无视同阵营减免。 */
    public static void runBypassingSameFaction(Runnable action) {
        bypassSameFactionDepth++;
        try {
            action.run();
        } finally {
            bypassSameFactionDepth--;
        }
    }

    /** 当前是否处于"无视同阵营减免"的结算期间。 */
    public static boolean isBypassingSameFaction() {
        return bypassSameFactionDepth > 0;
    }
}
