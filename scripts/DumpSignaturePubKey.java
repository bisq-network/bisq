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

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

final class DumpSignaturePubKey {
    private static final String SIGNATURE_KEY_FILE_NAME = "sig.key";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static void main(String[] args) {
        if (args.length == 1 && ("-h".equals(args[0]) || "--help".equals(args[0]))) {
            printUsage();
            return;
        }

        if (args.length != 1) {
            printUsage();
            System.exit(2);
        }

        try {
            Path keyPath = resolveKeyPath(Path.of(args[0]));
            byte[] encodedPrivateKey = Files.readAllBytes(keyPath);
            KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encodedPrivateKey));

            if (!(privateKey instanceof DSAPrivateKey dsaPrivateKey)) {
                throw new IllegalArgumentException(keyPath + " is not a DSA private key");
            }

            PublicKey publicKey = derivePublicKey(keyFactory, dsaPrivateKey);
            System.out.println(toHex(publicKey.getEncoded()));
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Path resolveKeyPath(Path inputPath) {
        Path keyPath = Files.isDirectory(inputPath) ? inputPath.resolve(SIGNATURE_KEY_FILE_NAME) : inputPath;
        if (!Files.isRegularFile(keyPath)) {
            throw new IllegalArgumentException("DSA key file not found: " + keyPath);
        }
        return keyPath;
    }

    private static PublicKey derivePublicKey(KeyFactory keyFactory, DSAPrivateKey privateKey)
            throws InvalidKeySpecException {
        DSAParams dsaParams = privateKey.getParams();
        BigInteger p = dsaParams.getP();
        BigInteger q = dsaParams.getQ();
        BigInteger g = dsaParams.getG();
        BigInteger y = g.modPow(privateKey.getX(), p);
        return keyFactory.generatePublic(new DSAPublicKeySpec(y, p, q, g));
    }

    private static String toHex(byte[] bytes) {
        char[] encoded = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            encoded[i * 2] = HEX[value >>> 4];
            encoded[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(encoded);
    }

    private static void printUsage() {
        System.err.println("usage: scripts/dump_signature_pub_key <sig.key|key-storage-dir>");
    }
}
