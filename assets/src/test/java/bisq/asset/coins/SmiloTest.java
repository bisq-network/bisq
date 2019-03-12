package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;


import org.junit.Test;


public class SmiloTest extends AbstractAssetTest {

    public SmiloTest() { super (new Smilo()); }

    @Test
    public void testValidAddresses() {
        assertValidAddress("0xb9b9d1C448cC839104de5CAe5718A336f962BBBA");
        assertValidAddress("0xb9b9d1C448cC837438de5CAe5718DDDDf962CCCA");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("0xb9b9d1c448cfff104de5cae5718a336f9622CCC3331111111111111111");
    }

}
