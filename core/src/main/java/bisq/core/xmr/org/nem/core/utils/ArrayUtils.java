package bisq.core.xmr.org.nem.core.utils;

import java.math.BigInteger;

/**
 * Static class that contains a handful of array helper functions.
 */
public class ArrayUtils {

    /**
     * Creates duplicate of given array
     *
     * @param src The array to duplicate.
     * @return A copy of the array.
     */
    public static byte[] duplicate(final byte[] src) {
        final byte[] result = new byte[src.length];
        System.arraycopy(src, 0, result, 0, src.length);
        return result;
    }

    /**
     * Concatenates byte arrays and returns the result.
     *
     * @param arrays The arrays.
     * @return A single array containing all elements in all arrays.
     */
    public static byte[] concat(final byte[]... arrays) {
        int totalSize = 0;
        for (final byte[] array : arrays) {
            totalSize += array.length;
        }

        int startIndex = 0;
        final byte[] result = new byte[totalSize];
        for (final byte[] array : arrays) {
            System.arraycopy(array, 0, result, startIndex, array.length);
            startIndex += array.length;
        }

        return result;
    }

    /**
     * Splits a single array into two arrays.
     *
     * @param bytes The input array.
     * @param splitIndex The index at which the array should be split.
     * @return Two arrays split at the splitIndex.
     * The first array will contain the first splitIndex elements.
     * The second array will contain all trailing elements.
     */
    public static byte[][] split(final byte[] bytes, final int splitIndex) {
        if (splitIndex < 0 || bytes.length < splitIndex) {
            throw new IllegalArgumentException("split index is out of range");
        }

        final byte[] lhs = new byte[splitIndex];
        final byte[] rhs = new byte[bytes.length - splitIndex];

        System.arraycopy(bytes, 0, lhs, 0, lhs.length);
        System.arraycopy(bytes, splitIndex, rhs, 0, rhs.length);
        return new byte[][]{lhs, rhs};
    }

    /**
     * Converts a BigInteger to a little endian byte array.
     *
     * @param value The value to convert.
     * @param numBytes The number of bytes in the destination array.
     * @return The resulting little endian byte array.
     */
    public static byte[] toByteArray(final BigInteger value, final int numBytes) {
        final byte[] outputBytes = new byte[numBytes];
        final byte[] bigIntegerBytes = value.toByteArray();

        int copyStartIndex = (0x00 == bigIntegerBytes[0]) ? 1 : 0;
        int numBytesToCopy = bigIntegerBytes.length - copyStartIndex;
        if (numBytesToCopy > numBytes) {
            copyStartIndex += numBytesToCopy - numBytes;
            numBytesToCopy = numBytes;
        }

        for (int i = 0; i < numBytesToCopy; ++i) {
            outputBytes[i] = bigIntegerBytes[copyStartIndex + numBytesToCopy - i - 1];
        }

        return outputBytes;
    }

    /**
     * Converts a little endian byte array to a BigInteger.
     *
     * @param bytes The bytes to convert.
     * @return The resulting BigInteger.
     */
    public static BigInteger toBigInteger(final byte[] bytes) {
        final byte[] bigEndianBytes = new byte[bytes.length + 1];
        for (int i = 0; i < bytes.length; ++i) {
            bigEndianBytes[i + 1] = bytes[bytes.length - i - 1];
        }

        return new BigInteger(bigEndianBytes);
    }

    /**
     * Constant-time byte[] comparison. The constant time behavior eliminates side channel attacks.
     *
     * @param b An array.
     * @param c An array.
     * @return 1 if b and c are equal, 0 otherwise.
     */
    public static int isEqualConstantTime(final byte[] b, final byte[] c) {
        int result = 0;
        result |= b.length - c.length;
        for (int i = 0; i < b.length; i++) {
            result |= b[i] ^ c[i];
        }

        return ByteUtils.isEqualConstantTime(result, 0);
    }

    /**
     * NON constant-time lexicographical byte[] comparison.
     *
     * @param b first of arrays to compare.
     * @param c second of arrays to compare.
     * @return 1, -1, or 0 depending on comparison result.
     */
    public static int compare(final byte[] b, final byte[] c) {
        int result = b.length - c.length;
        if (0 != result) {
            return result;
        }

        for (int i = 0; i < b.length; i++) {
            result = b[i] - c[i];
            if (0 != result) {
                return result;
            }
        }

        return 0;
    }

    /**
     * Gets the i'th bit of a byte array.
     *
     * @param h The byte array.
     * @param i The bit index.
     * @return The value of the i'th bit in h
     */
    public static int getBit(final byte[] h, final int i) {
        return (h[i >> 3] >> (i & 7)) & 1;
    }
}
