package bisq.asset.coins;

import bisq.asset.AddressValidationResult;
import bisq.asset.AddressValidator;
import bisq.asset.Coin;

import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.nio.charset.StandardCharsets;

public class Nilu extends Coin {

    public Nilu() {
        super("Nilu", "NILU", new NiluAddressValidator());
    }


    private static class NiluAddressValidator implements AddressValidator {

        private static final String upperOrLowerPattern = "(0x)?([0-9a-f]{40}|[0-9A-F]{40})";
        private static final String mixPattern = "(0x)?([0-9a-fA-F]{40})";

        @Override
        public AddressValidationResult validate(String address) {
            if (!isValidAddress(address))
                return AddressValidationResult.invalidStructure();

            return AddressValidationResult.validAddress();
        }

        private boolean isValidAddress(String eip55) {
            if (eip55 == null || eip55.isEmpty())
                return false;

            if (eip55.matches(upperOrLowerPattern))
                return true;

            if (!eip55.matches(mixPattern))
                return false;

            String addr = convertToEip55Address(eip55);
            return addr.replaceFirst("0x", "").equals(eip55.replaceFirst("0x", ""));
        }

        private String convertToEip55Address(String input) {
            String addr = input.replaceFirst("0x", "").toLowerCase();
            StringBuilder ret = new StringBuilder("0x");
            String hash = sha3String(addr).substring(2);
            for (int i = 0; i < addr.length(); i++) {
                String a = addr.charAt(i) + "";
                ret.append(Integer.parseInt(hash.charAt(i) + "", 16) > 7 ? a.toUpperCase() : a);
            }
            return ret.toString();
        }

        private static byte[] sha3(byte[] input, int offset, int length) {
            Keccak.DigestKeccak kecc = new Keccak.Digest256();
            kecc.update(input, offset, length);
            return kecc.digest();
        }

        private static byte[] sha3(byte[] input) {
            return sha3(input, 0, input.length);
        }

        private static String toHexString(byte[] input, int offset, int length, boolean withPrefix) {
            StringBuilder stringBuilder = new StringBuilder();
            if (withPrefix) {
                stringBuilder.append("0x");
            }

            for (int i = offset; i < offset + length; ++i) {
                stringBuilder.append(String.format("%02x", input[i] & 255));
            }

            return stringBuilder.toString();
        }

        private static String toHexString(byte[] input) {
            return toHexString(input, 0, input.length, true);
        }

        private static String sha3String(String utf8String) {
            return toHexString(sha3(utf8String.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
