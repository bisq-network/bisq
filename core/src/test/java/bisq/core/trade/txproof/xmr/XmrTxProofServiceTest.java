package bisq.core.trade.txproof.xmr;

import org.junit.Assert;
import org.junit.Test;

public class XmrTxProofServiceTest {
    @Test
    public void testIs32BitHexStringInValid() {
        Assert.assertFalse(XmrTxProofService.is32BitHexStringInValid("488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687d"));
        Assert.assertFalse(XmrTxProofService.is32BitHexStringInValid("488E48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687d"));
        Assert.assertTrue(XmrTxProofService.is32BitHexStringInValid("488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687da"));
        Assert.assertTrue(XmrTxProofService.is32BitHexStringInValid("488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687"));
        Assert.assertTrue(XmrTxProofService.is32BitHexStringInValid("488e48ab0c7e69028d19f787ec57fd496ff114caba9ab265bfd41a3ea0e4687Z"));
    }
}
