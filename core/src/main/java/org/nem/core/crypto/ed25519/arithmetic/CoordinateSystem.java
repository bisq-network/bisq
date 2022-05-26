package org.nem.core.crypto.ed25519.arithmetic;

/**
 * Available coordinate systems for a group element.
 */
public enum CoordinateSystem {

    /**
     * Affine coordinate system (x, y).
     */
    AFFINE,

    /**
     * Projective coordinate system (X:Y:Z) satisfying x=X/Z, y=Y/Z.
     */
    P2,

    /**
     * Extended projective coordinate system (X:Y:Z:T) satisfying x=X/Z, y=Y/Z, XY=ZT.
     */
    P3,

    /**
     * Completed coordinate system ((X:Z), (Y:T)) satisfying x=X/Z, y=Y/T.
     */
    P1xP1,

    /**
     * Precomputed coordinate system (y+x, y-x, 2dxy).
     */
    PRECOMPUTED,

    /**
     * Cached coordinate system (Y+X, Y-X, Z, 2dT).
     */
    CACHED
}
