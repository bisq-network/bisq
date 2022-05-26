package org.nem.core.crypto.ed25519;

import java.math.BigInteger;



import org.nem.core.crypto.ed25519.arithmetic.Ed25519Group;

/**
 * Class that wraps the elliptic curve Ed25519.
 */
public class Ed25519Curve implements org.nem.core.crypto.Curve {

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
