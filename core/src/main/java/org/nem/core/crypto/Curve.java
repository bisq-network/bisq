package org.nem.core.crypto;

import java.math.BigInteger;

/**
 * Interface for getting information for a curve.
 */
public interface Curve {

    /**
     * Gets the name of the curve.
     *
     * @return The name of the curve.
     */
    String getName();

    /**
     * Gets the group order.
     *
     * @return The group order.
     */
    BigInteger getGroupOrder();

    /**
     * Gets the group order / 2.
     *
     * @return The group order / 2.
     */
    BigInteger getHalfGroupOrder();
}
