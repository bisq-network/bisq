package bisq.core.xmr.org.nem.core.crypto.ed25519;

import bisq.core.xmr.org.nem.core.crypto.Curve;
import bisq.core.xmr.org.nem.core.crypto.ed25519.arithmetic.Ed25519Group;

import java.math.BigInteger;

/**
 * Class that wraps the elliptic curve Ed25519.
 */
public class Ed25519Curve implements Curve {

    private static final Ed25519Curve ED25519;

    static {
        ED25519 = new Ed25519Curve();
    }

    @Override
    public String getName() {
        return "ed25519";
    }

    @Override
    public BigInteger getGroupOrder() {
        return Ed25519Group.GROUP_ORDER;
    }

    @Override
    public BigInteger getHalfGroupOrder() {
        return Ed25519Group.GROUP_ORDER.shiftRight(1);
    }

    /**
     * Gets the Ed25519 instance.
     *
     * @return The Ed25519 instance.
     */
    public static Ed25519Curve ed25519() {
        return ED25519;
    }
}
