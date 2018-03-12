/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.util.validation.altcoins;

import bisq.desktop.util.validation.InputValidator;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by DevAlexey on 19.12.2016.
 */
public class ByteballAddressValidator {
    private static final Base32 base32 = new Base32();
    private static final Base64 base64 = new Base64();
    private static final String PI = "14159265358979323846264338327950288419716939937510";
    private static final String[] arrRelativeOffsets = PI.split("");
    @SuppressWarnings("CanBeFinal")
    private static Integer[] arrOffsets160;
    @SuppressWarnings("CanBeFinal")
    private static Integer[] arrOffsets288;

    static {
        try {
            arrOffsets160 = calcOffsets(160);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            arrOffsets288 = calcOffsets(288);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static InputValidator.ValidationResult validate(String input) {
        return new InputValidator.ValidationResult(isValidAddress(input));
    }

    private static boolean isValidAddress(String address) {
        return isValidChash(address, 32);
    }

    private static boolean isValidChash(String str, int len) {
        return (isStringOfLength(str, len) && isChashValid(str));
    }

    private static boolean isStringOfLength(String str, int len) {
        return str.length() == len;
    }

    private static void checkLength(int chash_length) throws Exception {
        if (chash_length != 160 && chash_length != 288)
            throw new Exception("unsupported c-hash length: " + chash_length);
    }

    private static Integer[] calcOffsets(int chash_length) throws Exception {
        checkLength(chash_length);
        List<Integer> arrOffsets = new ArrayList<>(chash_length);
        int offset = 0;
        int index = 0;

        for (int i = 0; offset < chash_length; i++) {
            int relative_offset = Integer.parseInt(arrRelativeOffsets[i]);
            if (relative_offset == 0)
                continue;
            offset += relative_offset;
            if (chash_length == 288)
                offset += 4;
            if (offset >= chash_length)
                break;
            arrOffsets.add(offset);
            //console.log("index="+index+", offset="+offset);
            index++;
        }

        if (index != 32)
            throw new Exception("wrong number of checksum bits");

        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return arrOffsets.toArray(new Integer[0]);
    }

    private static SeparatedData separateIntoCleanDataAndChecksum(String bin) throws Exception {
        int len = bin.length();
        Integer[] arrOffsets;
        if (len == 160)
            arrOffsets = arrOffsets160;
        else if (len == 288)
            arrOffsets = arrOffsets288;
        else
            throw new Exception("bad length");
        StringBuilder arrFrags = new StringBuilder();
        StringBuilder arrChecksumBits = new StringBuilder();
        int start = 0;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < arrOffsets.length; i++) {
            arrFrags.append(bin.substring(start, arrOffsets[i]));
            arrChecksumBits.append(bin.substring(arrOffsets[i], arrOffsets[i] + 1));
            start = arrOffsets[i] + 1;
        }
        // add last frag
        if (start < bin.length())
            arrFrags.append(bin.substring(start));
        String binCleanData = arrFrags.toString();
        String binChecksum = arrChecksumBits.toString();
        return new SeparatedData(binCleanData, binChecksum);
    }

    private static String buffer2bin(byte[] buf) {
        StringBuilder bytes = new StringBuilder();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < buf.length; i++) {
            String bin = String.format("%8s", Integer.toBinaryString(buf[i] & 0xFF)).replace(' ', '0');
            bytes.append(bin);
        }
        return bytes.toString();
    }

    private static byte[] bin2buffer(String bin) {
        int len = bin.length() / 8;
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++)
            buf[i] = (byte) Integer.parseInt(bin.substring(i * 8, (i + 1) * 8), 2);
        return buf;
    }

    private static boolean isChashValid(String encoded) {
        int encoded_len = encoded.length();
        if (encoded_len != 32 && encoded_len != 48) // 160/5 = 32, 288/6 = 48
            return false;
        byte[] chash = (encoded_len == 32) ? base32.decode(encoded) : base64.decode(encoded);
        String binChash = buffer2bin(chash);
        SeparatedData separated;
        try {
            separated = separateIntoCleanDataAndChecksum(binChash);
        } catch (Exception e) {
            return false;
        }
        byte[] clean_data = bin2buffer(separated.clean_data);
        byte[] checksum = bin2buffer(separated.checksum);
        return Arrays.equals(getChecksum(clean_data), checksum);
    }

    private static byte[] getChecksum(byte[] clean_data) {

        try {
            byte[] full_checksum = MessageDigest.getInstance("SHA-256").digest(clean_data);
            return new byte[]{full_checksum[5], full_checksum[13], full_checksum[21], full_checksum[29]};
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static class SeparatedData {
        final String clean_data;
        final String checksum;

        public SeparatedData(String clean_data, String checksum) {
            this.clean_data = clean_data;
            this.checksum = checksum;
        }
    }
}
