package bisq.core.trade.txproof.xmr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmrTxProofServiceTest {
    @Test
    public void testIs32BitHexStringInValid() {
        assertFalse(XmrTxProofService.is32BitHexStringInValid("488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687d"));
        assertFalse(XmrTxProofService.is32BitHexStringInValid("488E48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687d"));
        assertTrue(XmrTxProofService.is32BitHexStringInValid("488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687da"));
        assertTrue(XmrTxProofService.is32BitHexStringInValid("488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687"));
        assertTrue(XmrTxProofService.is32BitHexStringInValid("488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687Z"));
    }
}
