package bisq.core.xmr.org.nem.core.utils;

import bisq.core.xmr.knaccc.monero.address.ByteUtil;

// modified to not rely on apache commons

/**
 * Static class that contains utility functions for converting hex strings to and from bytes.
 */
public class HexEncoder {

    /**
     * Converts a hex string to a byte array.
     *
     * @param hexString The input hex string.
     * @return The output byte array.
     */
    public static byte[] getBytes(final String hexString) {
        try {
            return ByteUtil.hexToBytes(hexString);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Converts a byte array to a hex string.
     *
     * @param bytes The input byte array.
     * @return The output hex string.
     */
    public static String getString(final byte[] bytes) {
        return ByteUtil.bytesToHex(bytes);
    }
}
