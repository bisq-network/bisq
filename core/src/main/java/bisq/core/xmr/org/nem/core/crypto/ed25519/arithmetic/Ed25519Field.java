package bisq.core.xmr.org.nem.core.crypto.ed25519.arithmetic;

import bisq.core.xmr.org.nem.core.utils.ArrayUtils;
import bisq.core.xmr.org.nem.core.utils.HexEncoder;

import java.math.BigInteger;

/**
 * Represents the underlying finite field for Ed25519.
 * The field has p = 2^255 - 19 elements.
 */
public class Ed25519Field {

    /**
     * P: 2^255 - 19
     */
    public static final BigInteger P = new BigInteger(HexEncoder.getBytes("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed"));
    public static final Ed25519FieldElement ZERO = getFieldElement(0);
    public static final Ed25519FieldElement ONE = getFieldElement(1);
    public static final Ed25519FieldElement TWO = getFieldElement(2);
    public static final Ed25519FieldElement D = getD();
    public static final Ed25519FieldElement D_Times_TWO = D.multiply(TWO);
    public static final byte[] ZERO_SHORT = new byte[32];
    public static final byte[] ZERO_LONG = new byte[64];

    /**
     * I ^ 2 = -1
     */
    public static final Ed25519FieldElement I = new Ed25519EncodedFieldElement(HexEncoder.getBytes(
            "b0a00e4a271beec478e42fad0618432fa7d7fb3d99004d2b0bdfc14f8024832b")).decode();

    private static Ed25519FieldElement getFieldElement(final int value) {
        final int[] f = new int[10];
        f[0] = value;
        return new Ed25519FieldElement(f);
    }

    private static Ed25519FieldElement getD() {
        final BigInteger d = new BigInteger("-121665")
                .multiply(new BigInteger("121666").modInverse(Ed25519Field.P))
                .mod(Ed25519Field.P);
        return new Ed25519EncodedFieldElement(ArrayUtils.toByteArray(d, 32)).decode();
    }
}
