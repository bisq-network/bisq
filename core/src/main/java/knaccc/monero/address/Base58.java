package knaccc.monero.address;

import java.math.BigInteger;

public class Base58 {

    public static String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    public static int[] encoded_block_sizes = new int[]{0, 2, 3, 5, 6, 7, 9, 10, 11};

    public static int full_block_size = 8;
    public static int full_encoded_block_size = 11;

    public static BigInteger UINT64_MAX = BigInteger.valueOf(2).pow(64);

    public static int lookupIndex(int[] array, int match) {
        int index = -1;
        for (int i = 0; i < array.length; i++) if (array[i] == match) index = i;
        return index;
    }

    private static byte[] decode_block(String data, byte[] buf, int index) {

        if (data.length() < 1 || data.length() > full_encoded_block_size) {
            throw new RuntimeException("Invalid block length: " + data.length());
        }

        int res_size = lookupIndex(encoded_block_sizes, data.length());
        if (res_size <= 0) {
            throw new RuntimeException("Invalid block size");
        }

        BigInteger res_num = BigInteger.ZERO;
        BigInteger order = BigInteger.ONE;
        for (int i = data.length() - 1; i >= 0; i--) {

            int digit = alphabet.indexOf(data.charAt(i));
            if (digit < 0) {
                throw new RuntimeException("Invalid symbol");
            }
            BigInteger product = order.multiply(BigInteger.valueOf(digit)).add(res_num);
            if (product.compareTo(UINT64_MAX) == 1) {
                throw new RuntimeException("Overflow");
            }
            res_num = product;
            order = order.multiply(BigInteger.valueOf(alphabet.length()));

        }
        if (res_size < full_block_size && (BigInteger.valueOf(2).pow(8 * res_size).compareTo(res_num) <= 0)) {
            throw new RuntimeException("Overflow 2");
        }

        byte[] a = uint64_to_8be(res_num, res_size);
        System.arraycopy(a, 0, buf, index, a.length);
        return buf;
    }

    private static byte[] uint64_to_8be(BigInteger n, int size) {
        byte[] r = new byte[size];
        byte[] a = n.toByteArray();
        if (a.length <= r.length) System.arraycopy(a, 0, r, r.length - a.length, a.length);
        else System.arraycopy(a, a.length - r.length, r, 0, r.length);
        return r;
    }

    public static byte[] decode(String enc) {

        int full_block_count = (int) Math.floor(enc.length() / full_encoded_block_size);
        int last_block_size = enc.length() % full_encoded_block_size;
        int last_block_decoded_size = -1;
        for (int i = 0; i < encoded_block_sizes.length; i++)
            if (encoded_block_sizes[i] == last_block_size) last_block_decoded_size = i;
        if (last_block_decoded_size < 0) {
            throw new RuntimeException("Invalid encoded length");
        }
        int data_size = full_block_count * full_block_size + last_block_decoded_size;
        byte[] data = new byte[data_size];
        for (int i = 0; i < full_block_count; i++) {
            data = decode_block(enc.substring(i * full_encoded_block_size, i * full_encoded_block_size + full_encoded_block_size), data, i * full_block_size);
        }
        if (last_block_size > 0) {
            data = decode_block(enc.substring(full_block_count * full_encoded_block_size, full_block_count * full_encoded_block_size + last_block_size), data, full_block_count * full_block_size);
        }
        return data;

    }

    private static void encode_block(byte[] data, char[] buf, int index) {
        if (data.length < 1 || data.length > full_encoded_block_size) {
            throw new RuntimeException("Invalid block length: " + data.length);
        }

        byte[] dataZeroPadded = new byte[data.length + 1];
        System.arraycopy(data, 0, dataZeroPadded, 1, data.length);

        BigInteger num = new BigInteger(dataZeroPadded);
        int i = encoded_block_sizes[data.length] - 1;
        while (num.compareTo(BigInteger.ZERO) == 1) {
            BigInteger[] div = num.divideAndRemainder(BigInteger.valueOf(alphabet.length()));
            BigInteger remainder = div[1];
            num = div[0];
            buf[index + i] = alphabet.charAt(remainder.intValue());
            i--;
        }
    }

    public static String encode(byte[] data) {

        int full_block_count = (int) Math.floor(data.length / full_block_size);
        int last_block_size = data.length % full_block_size;
        int res_size = full_block_count * full_encoded_block_size + encoded_block_sizes[last_block_size];

        char[] res = new char[res_size];
        for (int i = 0; i < res_size; ++i) {
            res[i] = alphabet.charAt(0);
        }
        for (int i = 0; i < full_block_count; i++) {
            encode_block(subarray(data, i * full_block_size, i * full_block_size + full_block_size), res, i * full_encoded_block_size);
        }
        if (last_block_size > 0) {
            encode_block(subarray(data, full_block_count * full_block_size, full_block_count * full_block_size + last_block_size), res, full_block_count * full_encoded_block_size);
        }
        return new String(res);

    }

    private static byte[] subarray(byte[] a, int start, int end) {
        byte[] r = new byte[end - start];
        System.arraycopy(a, start, r, 0, r.length);
        return r;
    }

}
