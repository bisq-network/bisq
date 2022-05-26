package org.nem.core.utils;

import java.nio.ByteBuffer;

public class ByteUtils {

    /**
     * Converts an array of 8 bytes into a long.
     *
     * @param bytes The bytes.
     * @return The long.
     */
    public static long bytesToLong(final byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(bytes, 0, 8);
        buffer.flip();
        return buffer.getLong();
    }

    /**
     * Converts a long value into an array of 8 bytes.
     *
     * @param x The long.
     * @return The bytes.
     */
    public static byte[] longToBytes(final long x) {
        final ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(x);
        return buffer.array();
    }

    /**
     * Converts an array of 4 bytes into a int.
     *
     * @param bytes The bytes.
     * @return The int.
     */
    public static int bytesToInt(final byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.put(bytes, 0, 4);
        buffer.flip();
        return buffer.getInt();
    }

    /**
     * Converts an int value into an array of 4 bytes.
     *
     * @param x The int.
     * @return The bytes.
     */
    public static byte[] intToBytes(final int x) {
        final ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(x);
        return buffer.array();
    }

    /**
     * Constant-time byte comparison. The constant time behavior eliminates side channel attacks.
     *
     * @param b One byte.
     * @param c Another byte.
     * @return 1 if b and c are equal, 0 otherwise.
     */
    public static int isEqualConstantTime(final int b, final int c) {
        int result = 0;
        final int xor = b ^ c;
        for (int i = 0; i < 8; i++) {
            result |= xor >> i;
        }

        return (result ^ 0x01) & 0x01;
    }

    /**
     * Constant-time check if byte is negative. The constant time behavior eliminates side channel attacks.
     *
     * @param b The byte to check.
     * @return 1 if the byte is negative, 0 otherwise.
     */
    public static int isNegativeConstantTime(final int b) {
        return (b >> 8) & 1;
    }

    /**
     * Creates a human readable representation of an array of bytes.
     *
     * @param bytes The bytes.
     * @return An string representation of the bytes.
     */
    public static String toString(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder();
        builder.append("{ ");
        for (final byte b : bytes) {
            builder.append(String.format("%02X ", (byte) (0xFF & b)));
        }

        builder.append("}");
        return builder.toString();
    }
}
