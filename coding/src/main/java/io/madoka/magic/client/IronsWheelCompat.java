package io.madoka.magic.client;

import io.redspace.ironsspellbooks.player.ExtendedKeyMapping;
import io.redspace.ironsspellbooks.player.KeyMappings;

final class IronsWheelCompat {
    private IronsWheelCompat() {
    }

    /** Iron's keeps a press latch even after vanilla clears the input when a screen opens. */
    static void consumeSharedPress() {
        consumeIfShared(KeyMappings.SPELL_WHEEL_KEYMAP);
        consumeIfShared(KeyMappings.SPELL_WHEEL_TOGGLE_KEYMAP);
    }

    private static void consumeIfShared(ExtendedKeyMapping mapping) {
        if (mapping.isActiveAndMatches(MadokaKeys.OPEN_WHEEL.getKey())) {
            mapping.consume();
        }
    }
}
