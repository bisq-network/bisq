package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

public class NiluTest extends AbstractAssetTest {

    public NiluTest() {
        super(new Nilu());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("0x9f5cca390496647b0a9a90803da67af7b9c11eee");
        assertValidAddress("0x9F5CCA390496647B0A9A90803DA67AF7B9C11EEE");
        assertValidAddress("0x9f5cca390496647B0a9A90803Da67af7b9C11EEE");
        assertValidAddress("9f5cca390496647b0a9a90803da67af7b9c11eee");
        assertValidAddress("9F5CCA390496647B0A9A90803DA67AF7B9C11EEE");
        assertValidAddress("9f5cca390496647B0a9A90803Da67af7b9C11EEE");
    }

    @Override
    public void testInvalidAddresses() {
        assertInvalidAddress("0x9f5xca390496647b0a9a90803da67af7b9c11eee");
        assertInvalidAddress("0x9F5CCA390496647B0A9A90803DA67AF7B9C11EEE22");
        assertInvalidAddress("0x9F5CCA390496647B0A9A90803DA67AF7B9C11EE");
        assertInvalidAddress("0x9F5ccA390496647b0a9A90803Da67AF7B9C11EEe");
        assertInvalidAddress("9F5CCA390496647B0A9A90803DA67AF7B9C11EEE22");
        assertInvalidAddress("9F5CCA390496647B0A9A90803DA67AF7B9C11EE");
    }
}
