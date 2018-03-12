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
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.util.validation.altcoins;

import bisq.desktop.util.validation.InputValidator.ValidationResult;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.VersionedChecksummedBytes;
import org.bitcoinj.params.Networks;

import java.io.ByteArrayOutputStream;

import java.util.Arrays;
import java.util.Locale;

import javax.annotation.Nullable;

public class WMCCAddressValidator {
    public static ValidationResult ValidateAddress(NetworkParameters params, String address) {
        if (!isLowerCase(address)) {
            try {
                Address.fromBase58(params, address);
                return new ValidationResult(true);
            } catch (AddressFormatException e) {
                return new ValidationResult(false, "Invalid Base58 Addr: " + e.getMessage());
            }
        }

        try {
            WitnessAddress.fromBech32(params, address);
            return new ValidationResult(true);
        } catch (AddressFormatException e) {
            return new ValidationResult(false, "Invalid Bech32 Addr: " + e.getMessage());
        }
    }

    private static boolean isLowerCase(String str) {
        char ch;
        for (int i = 0; i < str.length(); i++) {
            ch = str.charAt(i);
            if (Character.isDigit(ch))
                continue;
            if (!Character.isLowerCase(ch))
                return false;
        }
        return true;
    }
}

/* Add to support bech32 address*/
class WitnessAddress extends VersionedChecksummedBytes {
    public static final int PROGRAM_LENGTH_PKH = 20;
    public static final int PROGRAM_LENGTH_SH = 32;
    public static final int PROGRAM_MIN_LENGTH = 2;
    public static final int PROGRAM_MAX_LENGTH = 40;
    public static final String WITNESS_ADDRESS_HRP = "wc";

    private WitnessAddress(NetworkParameters params, byte[] data) throws AddressFormatException {
        super(params.getAddressHeader(), data);
        if (data.length < 1)
            throw new AddressFormatException("Zero data found");
        final int version = getWitnessVersion();
        if (version < 0 || version > 16)
            throw new AddressFormatException("Invalid script version: " + version);
        byte[] program = getWitnessProgram();
        if (program.length < PROGRAM_MIN_LENGTH || program.length > PROGRAM_MAX_LENGTH)
            throw new AddressFormatException("Invalid length: " + program.length);
        if (version == 0 && program.length != PROGRAM_LENGTH_PKH
                && program.length != PROGRAM_LENGTH_SH)
            throw new AddressFormatException(
                    "Invalid length for address version 0: " + program.length);
    }

    public int getWitnessVersion() {
        return bytes[0] & 0xff;
    }

    public byte[] getWitnessProgram() {
        return convertBits(bytes, 1, bytes.length - 1, 5, 8, false);
    }

    public static WitnessAddress fromBech32(@Nullable NetworkParameters params, String bech32)
            throws AddressFormatException {
        Bech32.Bech32Data bechData = Bech32.decode(bech32);
        if (params == null) {
            for (NetworkParameters p : Networks.get()) {
                if (bechData.hrp.equals(WITNESS_ADDRESS_HRP))
                    return new WitnessAddress(p, bechData.data);
            }
            throw new AddressFormatException("Invalid Prefix: No network found for " + bech32);
        } else {
            if (bechData.hrp.equals(WITNESS_ADDRESS_HRP))
                return new WitnessAddress(params, bechData.data);
            throw new AddressFormatException("Wrong Network: " + bechData.hrp);
        }
    }

    /**
     * Helper
     */
    private static byte[] convertBits(final byte[] in, final int inStart, final int inLen, final int fromBits,
                                      final int toBits, final boolean pad) throws AddressFormatException {
        int acc = 0;
        int bits = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        final int maxv = (1 << toBits) - 1;
        final int max_acc = (1 << (fromBits + toBits - 1)) - 1;
        for (int i = 0; i < inLen; i++) {
            int value = in[i + inStart] & 0xff;
            if ((value >>> fromBits) != 0) {
                throw new AddressFormatException(
                        String.format("Input value '%X' exceeds '%d' bit size", value, fromBits));
            }
            acc = ((acc << fromBits) | value) & max_acc;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                out.write((acc >>> bits) & maxv);
            }
        }
        if (pad) {
            if (bits > 0)
                out.write((acc << (toBits - bits)) & maxv);
        } else if (bits >= fromBits || ((acc << (toBits - bits)) & maxv) != 0) {
            throw new AddressFormatException("Could not convert bits, invalid padding");
        }
        return out.toByteArray();
    }
}

/* Bech32 decoder */
class Bech32 {
    private static final byte[] CHARSET_REV = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 15, -1, 10, 17, 21, 20, 26, 30, 7, 5, -1, -1, -1, -1, -1, -1,
            -1, 29, -1, 24, 13, 25, 9, 8, 23, -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1,
            -1, 29, -1, 24, 13, 25, 9, 8, 23, -1, 18, 22, 31, 27, 19, -1, 1, 0, 3, 16, 11, 28, 12, 14, 6, 4, 2, -1, -1, -1, -1, -1
    };

    public static class Bech32Data {
        final String hrp;
        final byte[] data;

        private Bech32Data(final String hrp, final byte[] data) {
            this.hrp = hrp;
            this.data = data;
        }
    }

    private static int polymod(final byte[] values) {
        int c = 1;
        for (byte v_i : values) {
            int c0 = (c >>> 25) & 0xff;
            c = ((c & 0x1ffffff) << 5) ^ (v_i & 0xff);
            if ((c0 & 1) != 0) c ^= 0x3b6a57b2;
            if ((c0 & 2) != 0) c ^= 0x26508e6d;
            if ((c0 & 4) != 0) c ^= 0x1ea119fa;
            if ((c0 & 8) != 0) c ^= 0x3d4233dd;
            if ((c0 & 16) != 0) c ^= 0x2a1462b3;
        }
        return c;
    }

    private static byte[] expandHrp(final String hrp) {
        int len = hrp.length();
        byte ret[] = new byte[len * 2 + 1];
        for (int i = 0; i < len; ++i) {
            int c = hrp.charAt(i) & 0x7f;
            ret[i] = (byte) ((c >>> 5) & 0x07);
            ret[i + len + 1] = (byte) (c & 0x1f);
        }
        ret[len] = 0;
        return ret;
    }

    private static boolean verifyChecksum(final String hrp, final byte[] values) {
        byte[] exp = expandHrp(hrp);
        byte[] combined = new byte[exp.length + values.length];
        System.arraycopy(exp, 0, combined, 0, exp.length);
        System.arraycopy(values, 0, combined, exp.length, values.length);
        return polymod(combined) == 1;
    }

    public static Bech32Data decode(final String str) throws AddressFormatException {
        boolean lower = false, upper = false;
        int len = str.length();
        if (len < 8)
            throw new AddressFormatException("Input too short: " + len);
        if (len > 90)
            throw new AddressFormatException("Input too long: " + len);
        for (int i = 0; i < len; ++i) {
            char c = str.charAt(i);
            if (c < 33 || c > 126) throw new AddressFormatException(invalidChar(c, i));
            if (c >= 'a' && c <= 'z') {
                if (upper)
                    throw new AddressFormatException(invalidChar(c, i));
                lower = true;
            }
            if (c >= 'A' && c <= 'Z') {
                if (lower)
                    throw new AddressFormatException(invalidChar(c, i));
                upper = true;
            }
        }
        final int pos = str.lastIndexOf('1');
        if (pos < 1) throw new AddressFormatException("Invalid Prefix: Missing human-readable part");
        final int dataLen = len - 1 - pos;
        if (dataLen < 6) throw new AddressFormatException("Data part too short: " + dataLen);
        byte[] values = new byte[dataLen];
        for (int i = 0; i < dataLen; ++i) {
            char c = str.charAt(i + pos + 1);
            if (CHARSET_REV[c] == -1) throw new AddressFormatException(invalidChar(c, i + pos + 1));
            values[i] = CHARSET_REV[c];
        }
        String hrp = str.substring(0, pos).toLowerCase(Locale.ROOT);
        if (!verifyChecksum(hrp, values)) throw new AddressFormatException("Invalid Checksum");
        return new Bech32Data(hrp, Arrays.copyOfRange(values, 0, values.length - 6));
    }

    private static String invalidChar(char c, int i) {
        return "Invalid character '" + Character.toString(c) + "' at position " + i;
    }

    ;
}
