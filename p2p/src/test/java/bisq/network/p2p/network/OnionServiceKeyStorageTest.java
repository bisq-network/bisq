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

import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.NamedParameterSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import java.io.IOException;

import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OnionServiceKeyStorageTest {
    // Test vector 1 of RFC 8032, section 7.1. The onion address and the expanded secret key were computed
    // independently from its seed and its public key d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a.
    private static final byte[] RFC_8032_SEED =
            HexFormat.of().parseHex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
    private static final String RFC_8032_ONION_ADDRESS =
            "25njqamcweflpvkl73j4szahhihoc4xt3ktcgjnpaingr5yhkenl5sid.onion";
    private static final String RFC_8032_EXPANDED_SECRET_KEY =
            "MHyDhk8oM8tCei7xwAoBPP3/J2jZgMCjpSDwBpBN6U+bTwr+KAt0aneGhOdUQlAgV7dHOgPwj5b1o46Sh+Afjw==";
    private static final String OTHER_ONION_ADDRESS = "33bozyjr2mlx5ltgwsux7olvyypb23wsm5w3lbikvpank4lnn3cwbcqd.onion";
    // What netlayer wrote: jtorctl put the key of Tor's ADD_ONION reply "PrivateKey=ED25519-V3:<key>" between these
    // lines, without a line break at the end. The layout was checked with a private_key file which netlayer wrote.
    private static final String RFC_8032_NETLAYER_PRIVATE_KEY = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            RFC_8032_EXPANDED_SECRET_KEY + "\n-----END OPENSSH PRIVATE KEY-----";

    @ParameterizedTest
    @CsvSource({
            "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60, " +
                    "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
            "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb, " +
                    "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
            "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7, " +
                    "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025",
            // "SHA(abc)", whose public key has an odd x
            "833fe62409237b9d62ec77587520911e9a759cec1d19755b7da901b96dca3d42, " +
                    "ec172b93ad5e563bf4932c70e1245034c35467ef2efd4d64ebf819683467e2bf"
    })
    void derivesThePublicKeysOfTheRfc8032TestVectors(String seed, String publicKey) {
        byte[] expandedSecretKey = OnionServiceKeyStorage.toExpandedSecretKey(HexFormat.of().parseHex(seed));

        assertEquals(publicKey, HexFormat.of().formatHex(OnionServiceKeyStorage.derivePublicKey(expandedSecretKey)));
    }

    @Test
    void derivesThePublicKeyWhichTheJdkComputesFromTheSeed() throws Exception {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 50; i++) {
            byte[] seed = new byte[32];
            random.nextBytes(seed);
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
            keyPairGenerator.initialize(NamedParameterSpec.ED25519, new FixedSeedRandom(seed));
            EdECPoint point = ((EdECPublicKey) keyPairGenerator.generateKeyPair().getPublic()).getPoint();

            byte[] expandedSecretKey = OnionServiceKeyStorage.toExpandedSecretKey(seed);

            assertArrayEquals(encode(point), OnionServiceKeyStorage.derivePublicKey(expandedSecretKey));
        }
    }

    @Test
    void generatesTheOnionAddressAndTheExpandedSecretKeyOfTheSeed() {
        OnionServiceKey onionServiceKey = OnionServiceKeyStorage.generate(new FixedSeedRandom(RFC_8032_SEED));

        assertEquals(RFC_8032_ONION_ADDRESS, onionServiceKey.getOnionAddress());
        assertEquals(RFC_8032_EXPANDED_SECRET_KEY, onionServiceKey.getBase64SecretScalar());
    }

    @Test
    void loadsTheKeyFilesWrittenByNetlayer(@TempDir Path hiddenServiceDir) throws IOException {
        Files.writeString(hiddenServiceDir.resolve("private_key"), RFC_8032_NETLAYER_PRIVATE_KEY);
        Files.writeString(hiddenServiceDir.resolve("hostname"), RFC_8032_ONION_ADDRESS);

        OnionServiceKey onionServiceKey = OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir);

        assertEquals(RFC_8032_ONION_ADDRESS, onionServiceKey.getOnionAddress());
        assertEquals(RFC_8032_EXPANDED_SECRET_KEY, onionServiceKey.getBase64SecretScalar());
        assertEquals(RFC_8032_NETLAYER_PRIVATE_KEY, Files.readString(hiddenServiceDir.resolve("private_key")));
        assertEquals(RFC_8032_ONION_ADDRESS, Files.readString(hiddenServiceDir.resolve("hostname")));
    }

    @Test
    void loadsAKeyInTheFormatOfTheControlPort(@TempDir Path hiddenServiceDir) throws IOException {
        Files.writeString(hiddenServiceDir.resolve("private_key"), "ED25519-V3:" + RFC_8032_EXPANDED_SECRET_KEY);
        Files.writeString(hiddenServiceDir.resolve("hostname"), RFC_8032_ONION_ADDRESS + "\n");

        OnionServiceKey onionServiceKey = OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir);

        assertEquals(RFC_8032_ONION_ADDRESS, onionServiceKey.getOnionAddress());
        assertEquals(RFC_8032_EXPANDED_SECRET_KEY, onionServiceKey.getBase64SecretScalar());
        assertEquals("ED25519-V3:" + RFC_8032_EXPANDED_SECRET_KEY,
                Files.readString(hiddenServiceDir.resolve("private_key")));
        assertEquals(RFC_8032_ONION_ADDRESS + "\n", Files.readString(hiddenServiceDir.resolve("hostname")));
    }

    @Test
    void earlierVersionsCanUseAGeneratedKey(@TempDir Path dataDir) throws IOException {
        Path hiddenServiceDir = dataDir.resolve("hiddenservice");
        OnionServiceKey created = OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir);

        // How netlayer's jtorctl turned the file into the key argument of ADD_ONION (getEncodePrivateKey)
        String privateKey = Files.readString(hiddenServiceDir.resolve("private_key"), StandardCharsets.UTF_8);
        String addOnionKey = "ED25519-V3:" + privateKey
                .replaceAll("-----(BEGIN|END) ?[A-Z]* PRIVATE KEY-----", "")
                .replaceAll("\n", "");

        assertEquals("ED25519-V3:" + created.getBase64SecretScalar(), addOnionKey);
    }

    @Test
    void writesAMissingHostname(@TempDir Path hiddenServiceDir) throws IOException {
        Files.writeString(hiddenServiceDir.resolve("private_key"), "ED25519-V3:" + RFC_8032_EXPANDED_SECRET_KEY);

        OnionServiceKey onionServiceKey = OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir);

        assertEquals(RFC_8032_ONION_ADDRESS, onionServiceKey.getOnionAddress());
        assertEquals(RFC_8032_ONION_ADDRESS, Files.readString(hiddenServiceDir.resolve("hostname")));
        assertEquals("ED25519-V3:" + RFC_8032_EXPANDED_SECRET_KEY,
                Files.readString(hiddenServiceDir.resolve("private_key")));
    }

    @Test
    void replacesAHostnameWhichDoesNotBelongToTheKey(@TempDir Path hiddenServiceDir) throws IOException {
        Files.writeString(hiddenServiceDir.resolve("private_key"), "ED25519-V3:" + RFC_8032_EXPANDED_SECRET_KEY);
        Files.writeString(hiddenServiceDir.resolve("hostname"), OTHER_ONION_ADDRESS);

        OnionServiceKey onionServiceKey = OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir);

        assertEquals(RFC_8032_ONION_ADDRESS, onionServiceKey.getOnionAddress());
        assertEquals(RFC_8032_ONION_ADDRESS, Files.readString(hiddenServiceDir.resolve("hostname")));
    }

    @Test
    void createsTheKeyFilesAtTheFirstStart(@TempDir Path dataDir) throws IOException {
        Path hiddenServiceDir = dataDir.resolve("hiddenservice");

        OnionServiceKey created = OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir);
        OnionServiceKey loaded = OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir);

        assertEquals(created.getOnionAddress(), loaded.getOnionAddress());
        assertEquals(created.getBase64SecretScalar(), loaded.getBase64SecretScalar());
        assertEquals(created.getOnionAddress(),
                Files.readString(hiddenServiceDir.resolve("hostname"), StandardCharsets.UTF_8));
        assertEquals(OnionServiceKeyStorage.toNetlayerFormat(created.getBase64SecretScalar()),
                Files.readString(hiddenServiceDir.resolve("private_key"), StandardCharsets.UTF_8));
        assertEquals(64, Base64.getDecoder().decode(created.getBase64SecretScalar()).length);
        try (var files = Files.list(hiddenServiceDir)) {
            assertEquals(2, files.count());
        }
        if (supportsPosixPermissions()) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(hiddenServiceDir)));
            Path privateKey = hiddenServiceDir.resolve("private_key");
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(privateKey)));
        }
    }

    @Test
    void restrictsTheAccessToAnExistingKey(@TempDir Path hiddenServiceDir) throws IOException {
        assumeTrue(supportsPosixPermissions());
        Path privateKey = hiddenServiceDir.resolve("private_key");
        Files.writeString(privateKey, "ED25519-V3:" + RFC_8032_EXPANDED_SECRET_KEY);
        Files.setPosixFilePermissions(privateKey, PosixFilePermissions.fromString("rw-r--r--"));

        OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir);

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(privateKey)));
    }

    @Test
    void rejectsAKeyOfAnotherType(@TempDir Path hiddenServiceDir) throws IOException {
        Files.writeString(hiddenServiceDir.resolve("private_key"), "RSA1024:MIICXAIBAAKBgQ");

        assertThrows(IOException.class, () -> OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir));
    }

    @Test
    void rejectsTheRsaKeyOfAVersion2OnionService(@TempDir Path hiddenServiceDir) throws IOException {
        Path privateKey = hiddenServiceDir.resolve("private_key");
        String rsaKey = "-----BEGIN RSA PRIVATE KEY-----\nMIICXAIBAAKBgQ\n-----END RSA PRIVATE KEY-----";
        Files.writeString(privateKey, rsaKey);

        IOException exception = assertThrows(IOException.class,
                () -> OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir));
        assertTrue(exception.getMessage().contains("version 2"));
        assertEquals(rsaKey, Files.readString(privateKey));
    }

    @Test
    void rejectsAKeyOfAnotherLength(@TempDir Path hiddenServiceDir) throws IOException {
        Path privateKey = hiddenServiceDir.resolve("private_key");
        Files.writeString(privateKey, "ED25519-V3:" + Base64.getEncoder().encodeToString(new byte[32]));

        assertThrows(IOException.class, () -> OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir));
        assertEquals("ED25519-V3:" + Base64.getEncoder().encodeToString(new byte[32]), Files.readString(privateKey));
    }

    @Test
    void rejectsASymbolicLink(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("target");
        Files.writeString(target, "ED25519-V3:" + RFC_8032_EXPANDED_SECRET_KEY);
        Path hiddenServiceDir = Files.createDirectories(dir.resolve("hiddenservice"));
        try {
            Files.createSymbolicLink(hiddenServiceDir.resolve("private_key"), target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "Symbolic links are not supported: " + e);
        }

        assertThrows(IOException.class, () -> OnionServiceKeyStorage.loadOrCreate(hiddenServiceDir));
    }

    private static boolean supportsPosixPermissions() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    // RFC 8032, section 5.1.2
    private static byte[] encode(EdECPoint point) {
        byte[] bigEndianY = point.getY().toByteArray();
        byte[] encoded = new byte[32];
        for (int i = 0; i < encoded.length && i < bigEndianY.length; i++) {
            encoded[i] = bigEndianY[bigEndianY.length - 1 - i];
        }
        if (point.isXOdd()) {
            encoded[31] |= (byte) 0x80;
        }
        return encoded;
    }

    private static final class FixedSeedRandom extends SecureRandom {
        private final byte[] seed;

        private FixedSeedRandom(byte[] seed) {
            this.seed = seed;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            System.arraycopy(seed, 0, bytes, 0, bytes.length);
        }
    }
}
