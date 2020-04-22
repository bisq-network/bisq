package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

public class AureusNummusGoldTest extends AbstractAssetTest {

    public AureusNummusGoldTest() {
        super(new AureusNummusGold());
    }

    @Override
    public void testValidAddresses() {
        assertValidAddress("0x8adccbd56ed6062485a9c6fc89dd899beaae9c02");
        assertValidAddress("0xa056320c8de334a272718f85ae23e3608b064c1b");
    }

    @Override
    public void testInvalidAddresses() {
        testNoXInBegin();
        testLongerThanExpected();
        testNo0InBegin();
        testUnexpectedSymbol();
    }

    private void testNoXInBegin() {
        assertInvalidAddress("08adccbd56ed6062485a9c6fc89dd899beaae9c02");
    }

    private void testLongerThanExpected() {
        assertInvalidAddress("0xa056320c8de334a272718f85ae23e3608b064c1ba");
    }

    private void testNo0InBegin() {
        assertInvalidAddress("axa056320c8de334a272718f85e23e3608b064c1ba");
    }

    private void testUnexpectedSymbol() {
        assertInvalidAddress("0x8adccbd56ed606248.a9c6fc89dd899beaae9c02");
    }
}
