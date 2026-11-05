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

package bisq.network.p2p.network;

import bisq.network.tor.onionservice.OnionServiceKey;

import com.google.common.io.BaseEncoding;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

import java.io.IOException;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.Base64;

import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the key of the node's onion service in the files which netlayer wrote, so that the onion address does not
 * change: private_key holds the Base64 of the expanded secret key, which Tor's ADD_ONION takes, in the armor which
 * netlayer's jtorctl wrote, and hostname holds the onion address. The onion address is derived from the key, as Tor
 * derives it, so hostname only shows it to the user. A key is only generated if there is no private_key file.
 */
@Slf4j
final class OnionServiceKeyStorage {
    private static final String PRIVATE_KEY_FILE_NAME = "private_key";
    private static final String HOSTNAME_FILE_NAME = "hostname";
    private static final String KEY_TYPE_PREFIX = "ED25519-V3:";
    // netlayer's jtorctl wrote the ED25519-V3 key of Tor's ADD_ONION reply between these lines, and removed them again
    // when it passed the key to Tor.
    private static final String NETLAYER_KEY_BEGIN = "-----BEGIN OPENSSH PRIVATE KEY-----";
    private static final String NETLAYER_KEY_END = "-----END OPENSSH PRIVATE KEY-----";
    private static final String NETLAYER_RSA_KEY_BEGIN = "-----BEGIN RSA PRIVATE KEY-----";
    private static final int EXPANDED_SECRET_KEY_LENGTH = 64;
    private static final byte ONION_ADDRESS_VERSION = 3;

    // Ed25519 (RFC 8032, section 5.1): the prime p, the curve constant d = -121665/121666 and the base point B
    private static final BigInteger P = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P))
            .mod(P);
    private static final BigInteger BASE_X =
            new BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202");
    private static final BigInteger BASE_Y =
            new BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960");

    private OnionServiceKeyStorage() {
    }

    static OnionServiceKey loadOrCreate(Path hiddenServiceDirPath) throws IOException {
        Path privateKeyPath = hiddenServiceDirPath.resolve(PRIVATE_KEY_FILE_NAME);
        Path hostnamePath = hiddenServiceDirPath.resolve(HOSTNAME_FILE_NAME);
        rejectSymbolicLink(privateKeyPath);
        rejectSymbolicLink(hostnamePath);
        boolean supportsPosixPermissions = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

        if (Files.exists(privateKeyPath, LinkOption.NOFOLLOW_LINKS)) {
            OnionServiceKey onionServiceKey = load(privateKeyPath);
            if (supportsPosixPermissions) {
                Files.setPosixFilePermissions(privateKeyPath, PosixFilePermissions.fromString("rw-------"));
            }
            writeHostnameIfDifferent(hostnamePath, onionServiceKey.getOnionAddress());
            return onionServiceKey;
        }
        // Files.exists is also false if the existence cannot be determined, for example without permission to read
        // the directory. A new key would then replace the identity of the node.
        if (!Files.notExists(privateKeyPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cannot determine whether " + privateKeyPath + " exists");
        }

        OnionServiceKey onionServiceKey = generate(new SecureRandom());
        Files.createDirectories(hiddenServiceDirPath);
        if (supportsPosixPermissions) {
            Files.setPosixFilePermissions(hiddenServiceDirPath, PosixFilePermissions.fromString("rwx------"));
        }
        // In netlayer's format, so that an earlier version of Bisq can still use the key.
        writeAtomically(privateKeyPath, toNetlayerFormat(onionServiceKey.getBase64SecretScalar()));
        writeAtomically(hostnamePath, onionServiceKey.getOnionAddress());
        return onionServiceKey;
    }

    static String toNetlayerFormat(String base64SecretScalar) {
        return NETLAYER_KEY_BEGIN + "\n" + base64SecretScalar + "\n" + NETLAYER_KEY_END;
    }

    static OnionServiceKey generate(SecureRandom random) {
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        byte[] expandedSecretKey = toExpandedSecretKey(seed);
        return new OnionServiceKey(toOnionAddress(derivePublicKey(expandedSecretKey)),
                Base64.getEncoder().encodeToString(expandedSecretKey));
    }

    // RFC 8032, section 5.1.5: the SHA-512 hash of the seed, with the secret scalar pruned in its first half
    static byte[] toExpandedSecretKey(byte[] seed) {
        try {
            byte[] expandedSecretKey = MessageDigest.getInstance("SHA-512").digest(seed);
            expandedSecretKey[0] &= (byte) 248;
            expandedSecretKey[31] &= 63;
            expandedSecretKey[31] |= 64;
            return expandedSecretKey;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // RFC 8032, sections 5.1.4 and 5.1.5: the public key is the encoded point s * B of the secret scalar s, which is
    // the first half of the expanded secret key in little-endian order. The JDK only computes it from the seed, which
    // Tor's key format does not contain. The computation is not constant-time; it runs once per start on a local key.
    static byte[] derivePublicKey(byte[] expandedSecretKey) {
        BigInteger scalar = new BigInteger(1, reverse(Arrays.copyOf(expandedSecretKey, 32)));
        BigInteger[] result = {BigInteger.ZERO, BigInteger.ONE};
        BigInteger[] addend = {BASE_X, BASE_Y};
        for (int i = 0; i < scalar.bitLength(); i++) {
            if (scalar.testBit(i)) {
                result = addPoints(result, addend);
            }
            addend = addPoints(addend, addend);
        }
        // RFC 8032, section 5.1.2: y in little-endian order, with the parity of x in the most significant bit
        byte[] publicKey = reverse(toUnsignedBytes(result[1]));
        if (result[0].testBit(0)) {
            publicKey[31] |= (byte) 0x80;
        }
        return publicKey;
    }

    private static OnionServiceKey load(Path privateKeyPath) throws IOException {
        String base64SecretScalar = readBase64SecretScalar(privateKeyPath);
        byte[] expandedSecretKey;
        try {
            expandedSecretKey = Base64.getDecoder().decode(base64SecretScalar);
        } catch (IllegalArgumentException e) {
            throw new IOException(privateKeyPath + " does not contain a valid " + KEY_TYPE_PREFIX + " key", e);
        }
        if (expandedSecretKey.length != EXPANDED_SECRET_KEY_LENGTH) {
            throw new IOException(privateKeyPath + " does not contain a valid " + KEY_TYPE_PREFIX + " key");
        }
        return new OnionServiceKey(toOnionAddress(derivePublicKey(expandedSecretKey)), base64SecretScalar);
    }

    // Accepts netlayer's format, which all existing nodes have, and the format of Tor's control port.
    private static String readBase64SecretScalar(Path privateKeyPath) throws IOException {
        String privateKey = Files.readString(privateKeyPath, StandardCharsets.UTF_8).trim();
        if (privateKey.startsWith(NETLAYER_KEY_BEGIN) && privateKey.endsWith(NETLAYER_KEY_END)) {
            return privateKey.substring(NETLAYER_KEY_BEGIN.length(), privateKey.length() - NETLAYER_KEY_END.length())
                    .replaceAll("\\s", "");
        }
        if (privateKey.startsWith(KEY_TYPE_PREFIX)) {
            return privateKey.substring(KEY_TYPE_PREFIX.length());
        }
        if (privateKey.startsWith(NETLAYER_RSA_KEY_BEGIN)) {
            throw new IOException(privateKeyPath + " contains the RSA key of a version 2 onion service, which Tor " +
                    "does not support anymore");
        }
        throw new IOException(privateKeyPath + " does not contain an " + KEY_TYPE_PREFIX + " key");
    }

    // hostname only shows the onion address to the user, so a failure to write it does not stop the start.
    private static void writeHostnameIfDifferent(Path hostnamePath, String onionAddress) {
        try {
            if (Files.exists(hostnamePath, LinkOption.NOFOLLOW_LINKS)) {
                String hostname = new String(Files.readAllBytes(hostnamePath), StandardCharsets.UTF_8).trim();
                if (hostname.equals(onionAddress)) {
                    return;
                }
                log.warn("{} contains {}, but private_key belongs to {}. The file gets the onion address of " +
                        "private_key.", hostnamePath, hostname, onionAddress);
            } else {
                log.info("{} is missing. The file gets the onion address {} of private_key.", hostnamePath,
                        onionAddress);
            }
            writeAtomically(hostnamePath, onionAddress);
        } catch (IOException e) {
            log.warn("Could not write {}", hostnamePath, e);
        }
    }

    // The temporary file is in the same directory, so that the move is atomic, and on POSIX file systems Java creates
    // it readable only by the owner. An interrupted write cannot leave a partial file.
    private static void writeAtomically(Path path, String content) throws IOException {
        Path tempPath = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(tempPath, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException(path + " must not be a symbolic link");
        }
    }

    // The addition law of the twisted Edwards curve -x^2 + y^2 = 1 + d x^2 y^2 (RFC 8032, section 5.1.4). It is
    // complete, so it also doubles a point and accepts the neutral element (0, 1).
    private static BigInteger[] addPoints(BigInteger[] p1, BigInteger[] p2) {
        BigInteger x1 = p1[0];
        BigInteger y1 = p1[1];
        BigInteger x2 = p2[0];
        BigInteger y2 = p2[1];
        BigInteger t = D.multiply(x1).multiply(x2).multiply(y1).multiply(y2).mod(P);
        BigInteger x3 = x1.multiply(y2).add(y1.multiply(x2))
                .multiply(BigInteger.ONE.add(t).mod(P).modInverse(P))
                .mod(P);
        BigInteger y3 = y1.multiply(y2).add(x1.multiply(x2))
                .multiply(BigInteger.ONE.subtract(t).mod(P).modInverse(P))
                .mod(P);
        return new BigInteger[]{x3, y3};
    }

    private static byte[] toUnsignedBytes(BigInteger value) {
        byte[] bigEndian = value.toByteArray();
        byte[] result = new byte[32];
        int length = Math.min(bigEndian.length, 32);
        System.arraycopy(bigEndian, bigEndian.length - length, result, 32 - length, length);
        return result;
    }

    private static byte[] reverse(byte[] bytes) {
        byte[] reversed = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            reversed[i] = bytes[bytes.length - 1 - i];
        }
        return reversed;
    }

    // Tor's rend-spec-v3, section 6: the public key, two bytes of a SHA3-256 checksum and the version, in base32
    private static String toOnionAddress(byte[] publicKey) {
        try {
            MessageDigest sha3 = MessageDigest.getInstance("SHA3-256");
            sha3.update(".onion checksum".getBytes(StandardCharsets.US_ASCII));
            sha3.update(publicKey);
            sha3.update(ONION_ADDRESS_VERSION);
            byte[] checksum = sha3.digest();
            byte[] address = ByteBuffer.allocate(publicKey.length + 3)
                    .put(publicKey)
                    .put(checksum, 0, 2)
                    .put(ONION_ADDRESS_VERSION)
                    .array();
            return BaseEncoding.base32().lowerCase().omitPadding().encode(address) + ".onion";
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
