package bisq.core.xmr.org.nem.core.crypto.ed25519.arithmetic;

import bisq.core.xmr.org.nem.core.utils.ArrayUtils;

import java.util.Arrays;

public class Ed25519EncodedGroupElement {

    private final byte[] values;

    /**
     * Creates a new encoded group element.
     *
     * @param values The values.
     */
    public Ed25519EncodedGroupElement(final byte[] values) {
        if (32 != values.length) {
            throw new IllegalArgumentException("Invalid encoded group element.");
        }

        this.values = values;
    }

    /**
     * Gets the underlying byte array.
     *
     * @return The byte array.
     */
    public byte[] getRaw() {
        return this.values;
    }

    /**
     * Decodes this encoded group element and returns a new group element in P3 coordinates.
     *
     * @return The group element.
     */
    public Ed25519GroupElement decode() {
        final Ed25519FieldElement x = this.getAffineX();
        final Ed25519FieldElement y = this.getAffineY();
        return Ed25519GroupElement.p3(x, y, Ed25519Field.ONE, x.multiply(y));
    }

    /**
     * Gets the affine x-coordinate.
     * x is recovered in the following way (p = field size):
     * <br>
     * x = sign(x) * sqrt((y^2 - 1) / (d * y^2 + 1)) = sign(x) * sqrt(u / v) with u = y^2 - 1 and v = d * y^2 + 1.
     * Setting β = (u * v^3) * (u * v^7)^((p - 5) / 8) one has β^2 = +-(u / v).
     * If v * β = -u multiply β with i=sqrt(-1).
     * Set x := β.
     * If sign(x) != bit 255 of s then negate x.
     *
     * @return the affine x-coordinate.
     */
    public Ed25519FieldElement getAffineX() {
        Ed25519FieldElement x;
        final Ed25519FieldElement y;
        final Ed25519FieldElement ySquare;
        final Ed25519FieldElement u;
        final Ed25519FieldElement v;
        final Ed25519FieldElement vxSquare;
        Ed25519FieldElement checkForZero;
        y = this.getAffineY();
        ySquare = y.square();

        // u = y^2 - 1
        u = ySquare.subtract(Ed25519Field.ONE);

        // v = d * y^2 + 1
        v = ySquare.multiply(Ed25519Field.D).add(Ed25519Field.ONE);

        // x = sqrt(u / v)
        x = Ed25519FieldElement.sqrt(u, v);

        vxSquare = x.square().multiply(v);
        checkForZero = vxSquare.subtract(u);
        if (checkForZero.isNonZero()) {
            checkForZero = vxSquare.add(u);
            if (checkForZero.isNonZero()) {
                throw new IllegalArgumentException("not a valid Ed25519EncodedGroupElement.");
            }

            x = x.multiply(Ed25519Field.I);
        }

        if ((x.isNegative() ? 1 : 0) != ArrayUtils.getBit(this.values, 255)) {
            x = x.negate();
        }

        return x;
    }

    /**
     * Gets the affine y-coordinate.
     *
     * @return the affine y-coordinate.
     */
    public Ed25519FieldElement getAffineY() {
        // The affine y-coordinate is in bits 0 to 254.
        // Since the decode() method of Ed25519EncodedFieldElement ignores bit 255,
        // we can use that method without problems.
        final Ed25519EncodedFieldElement encoded = new Ed25519EncodedFieldElement(this.values);
        return encoded.decode();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.values);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Ed25519EncodedGroupElement)) {
            return false;
        }

        final Ed25519EncodedGroupElement encoded = (Ed25519EncodedGroupElement) obj;
        return 1 == ArrayUtils.isEqualConstantTime(this.values, encoded.values);
    }

    @Override
    public String toString() {
        return String.format(
                "x=%s\ny=%s\n",
                this.getAffineX().toString(),
                this.getAffineY().toString());
    }
}
