package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

public class NanoTest extends AbstractAssetTest {
    public NanoTest() {
        super(new Nano());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("xrb_1j78msn5omp8jrjge8txwxm4x3smusa1cojg7nuk8fdzoux41fqeeogg5aa1");
        assertValidAddress("nano_1fuckbtc6p55wt64eo4rz7brq3ubjfd8unhz3it5fbdpta8tww7ywk8p9su7");
    }

    @Override
    public void testInvalidAddresses() {
        //exceed the limit
        assertInvalidAddress("xrb_1j78msn5omp8jrjge8txwxm4x3smusa1cojg7nuk8fdzoux41fqeeogg5aa111");
        //invalid prefix
        assertInvalidAddress("cda_1j78msn5omp8jrjge8txwxm4x3smusa1cojg7nuk8fdzoux41fqeeogg5aa1");
        //not valid address
        assertInvalidAddress("");
        assertInvalidAddress("not is an address");
    }
}
