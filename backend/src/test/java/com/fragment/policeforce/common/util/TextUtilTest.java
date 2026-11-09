package com.fragment.policeforce.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextUtilTest {

    @Test
    void shouldTrimTextAndNormalizeBlankValuesToNull() {
        assertNull(TextUtil.trimToNull(null));
        assertNull(TextUtil.trimToNull("   "));
        assertEquals("深圳巡防中队", TextUtil.trimToNull("  深圳巡防中队  "));
    }
}
