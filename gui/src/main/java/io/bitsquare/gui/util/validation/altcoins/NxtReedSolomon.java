/*
    Reed Solomon Encoding and Decoding for Nxt

    Version: 1.0, license: Public Domain, coder: NxtChg (admin@nxtchg.com)
    Java Version: ChuckOne (ChuckOne@mail.de).
*/

package io.bitsquare.gui.util.validation.altcoins;


public final class NxtReedSolomon {

    private static final int[] initial_codeword = {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] gexp = {1, 2, 4, 8, 16, 5, 10, 20, 13, 26, 17, 7, 14, 28, 29, 31, 27, 19, 3, 6, 12, 24, 21, 15, 30, 25, 23, 11, 22, 9, 18, 1};
    private static final int[] glog = {0, 0, 1, 18, 2, 5, 19, 11, 3, 29, 6, 27, 20, 8, 12, 23, 4, 10, 30, 17, 7, 22, 28, 26, 21, 25, 9, 16, 13, 14, 24, 15};
    private static final int[] codeword_map = {3, 2, 1, 0, 7, 6, 5, 4, 13, 14, 15, 16, 12, 8, 9, 10, 11};
    private static final String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final int base_32_length = 13;
    private static final int base_10_length = 20;

    public static String encode(long plain) {

        String plain_string = Long.toUnsignedString(plain);
        int length = plain_string.length();
        int[] plain_string_10 = new int[NxtReedSolomon.base_10_length];
        for (int i = 0; i < length; i++) {
            plain_string_10[i] = (int)plain_string.charAt(i) - (int)'0';
        }

        int codeword_length = 0;
        int[] codeword = new int[NxtReedSolomon.initial_codeword.length];

        do {  // base 10 to base 32 conversion
            int new_length = 0;
            int digit_32 = 0;
            for (int i = 0; i < length; i++) {
                digit_32 = digit_32 * 10 + plain_string_10[i];
                if (digit_32 >= 32) {
                    plain_string_10[new_length] = digit_32 >> 5;
                    digit_32 &= 31;
                    new_length += 1;
                } else if (new_length > 0) {
                    plain_string_10[new_length] = 0;
                    new_length += 1;
                }
            }
            length = new_length;
            codeword[codeword_length] = digit_32;
            codeword_length += 1;
        } while(length > 0);

        int[] p = {0, 0, 0, 0};
        for (int i = NxtReedSolomon.base_32_length - 1; i >= 0; i--) {
            final int fb = codeword[i] ^ p[3];
            p[3] = p[2] ^ NxtReedSolomon.gmult(30, fb);
            p[2] = p[1] ^ NxtReedSolomon.gmult(6, fb);
            p[1] = p[0] ^ NxtReedSolomon.gmult(9, fb);
            p[0] =        NxtReedSolomon.gmult(17, fb);
        }

        System.arraycopy(p, 0, codeword, NxtReedSolomon.base_32_length, NxtReedSolomon.initial_codeword.length - NxtReedSolomon.base_32_length);

        StringBuilder cypher_string_builder = new StringBuilder();
        for (int i = 0; i < 17; i++) {
            final int codework_index = NxtReedSolomon.codeword_map[i];
            final int alphabet_index = codeword[codework_index];
            cypher_string_builder.append(NxtReedSolomon.alphabet.charAt(alphabet_index));

            if ((i & 3) == 3 && i < 13) {
                cypher_string_builder.append('-');
            }
        }
        return cypher_string_builder.toString();
    }

    public static long decode(String cypher_string) throws DecodeException {

        int[] codeword = new int[NxtReedSolomon.initial_codeword.length];
        System.arraycopy(NxtReedSolomon.initial_codeword, 0, codeword, 0, NxtReedSolomon.initial_codeword.length);

        int codeword_length = 0;
        for (int i = 0; i < cypher_string.length(); i++) {
            int position_in_alphabet = NxtReedSolomon.alphabet.indexOf(cypher_string.charAt(i));

            if (position_in_alphabet <= -1 || position_in_alphabet > NxtReedSolomon.alphabet.length()) {
                continue;
            }

            if (codeword_length > 16) {
                throw new CodewordTooLongException();
            }

            int codework_index = NxtReedSolomon.codeword_map[codeword_length];
            codeword[codework_index] = position_in_alphabet;
            codeword_length += 1;
        }

        if (codeword_length == 17 && !NxtReedSolomon.is_codeword_valid(codeword) || codeword_length != 17) {
            throw new CodewordInvalidException();
        }

        int length = NxtReedSolomon.base_32_length;
        int[] cypher_string_32 = new int[length];
        for (int i = 0; i < length; i++) {
            cypher_string_32[i] = codeword[length - i - 1];
        }

        StringBuilder plain_string_builder = new StringBuilder();
        do { // base 32 to base 10 conversion
            int new_length = 0;
            int digit_10 = 0;

            for (int i = 0; i < length; i++) {
                digit_10 = digit_10 * 32 + cypher_string_32[i];

                if (digit_10 >= 10) {
                    cypher_string_32[new_length] = digit_10 / 10;
                    digit_10 %= 10;
                    new_length += 1;
                } else if (new_length > 0) {
                    cypher_string_32[new_length] = 0;
                    new_length += 1;
                }
            }
            length = new_length;
            plain_string_builder.append((char)(digit_10 + (int)'0'));
        } while (length > 0);

        return Long.parseUnsignedLong(plain_string_builder.reverse().toString());
    }

    private static int gmult(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }

        int idx = (NxtReedSolomon.glog[a] + NxtReedSolomon.glog[b]) % 31;

        return NxtReedSolomon.gexp[idx];
    }

    private static boolean is_codeword_valid(int[] codeword) {
        int sum = 0;

        for (int i = 1; i < 5; i++) {
            int t = 0;

            for (int j = 0; j < 31; j++) {
                if (j > 12 && j < 27) {
                    continue;
                }

                int pos = j;
                if (j > 26) {
                    pos -= 14;
                }

                t ^= NxtReedSolomon.gmult(codeword[pos], NxtReedSolomon.gexp[(i * j) % 31]);
            }

            sum |= t;
        }

        return sum == 0;
    }

    public abstract static class DecodeException extends Exception {
    }

    static final class CodewordTooLongException extends DecodeException {
    }

    static final class CodewordInvalidException extends DecodeException {
    }

    private NxtReedSolomon() {} // never
}


