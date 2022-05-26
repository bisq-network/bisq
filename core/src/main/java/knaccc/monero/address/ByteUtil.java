package knaccc.monero.address;

import java.math.BigInteger;

public class ByteUtil {

    public static String byteToHex(int v) {
        byte[] array = new byte[1];
        array[0] = (byte) v;
        return bytesToHex(array);
    }

    final protected static char[] hexArray = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static void reverseByteArrayInPlace(byte[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            byte temp = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = temp;
        }
    }

    public static byte[] concat(byte a, byte[] b) {
        byte[] r = new byte[1 + b.length];
        r[0] = a;
        System.arraycopy(b, 0, r, 1, b.length);
        return r;
    }

    public static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    public static byte[] concat(byte[] a, byte[] b, byte[] c, byte[] d) {
        return concat(concat(a, b), concat(c, d));
    }

    public static byte[] longToLittleEndianUint32ByteArray(long value) {
        byte[] array = new byte[4];
        for (int i = 3; i >= 0; i--) array[i] = (byte) (value >> i * 8);
        return array;
    }

    public static BigInteger getBigIntegerFromUnsignedLittleEndianByteArray(byte[] a1) {
        byte[] a = new byte[a1.length];
        System.arraycopy(a1, 0, a, 0, a1.length);
        reverseByteArrayInPlace(a);
        byte[] a2 = new byte[a1.length + 1];
        System.arraycopy(a, 0, a2, 1, a1.length);
        return new BigInteger(a2);
    }


}
