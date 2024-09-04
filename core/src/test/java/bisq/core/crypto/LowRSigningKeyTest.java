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

package bisq.core.crypto;

import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.Futures;

import java.nio.charset.StandardCharsets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LowRSigningKeyTest {
    /**
     * Deterministic low-R signature test vectors of the form {privKey, sigHash, rComponent, sComponent}, generated with
     * the aid of the Bitcoin Core tx creation and editing command: 'bitcoin-tx'. The signed txs and subsequent test
     * vectors here match those produced by Core/Knots version 27.1 (and likely other post-2018 versions).
     * @see TestVectorGeneration
     */
    private static final String[][] GENERATED_TEST_VECTORS = {
            {"02", "546876d08f9f06f12c168f96079cd0af996ce22d89bbc58eced2631918a9bc9a", "45cc5d5e4fad81f2bc89f16cb37575da3ae13677f707a73ca5ca1e2787e3d311", "25829e0ad206dbd53cd637b75eedbde3273548673b2d08de3f61dcbe723409e2"},
            {"02", "2cca2f04e6e654226483f5af39fbfebb883e301bd32553a6a0fb176b0d172b3a", "2bdcafead8f6db212228e52f061e894db8bdc2133c6c81a5b54883ef5648ae6d", "007bdcc92effb2931b4c7c5c834bf7aa847d3e0e0e1d9c7e41ed3981821eb830"},
            {"02", "d78c70bc5e7f9a74ce4e3000369592a037a8898a93b07a5bdc2d88ed78462521", "1f283dfd1ba17e69dbbe2fe261c3984569316efa781b1a4e846fa7978f0fe918", "180187c9cc33c2849b4770260a1ee379e5c4292ad46bd700c45a108bb4c6f346"},
            {"02", "c65a47a4d448b60cff8bbcf190492a1d5ef6beb217465c69bd358c4460ab84fb", "216301d61b337ca80c62047d349fa85c04b05451586ab0a2034d0855b09209fe", "228d09fe3e5a9a90501def65a8e467a5172c9fdd510c8a5db3a6cdbea000b1cc"},
            {"02", "d5bde8603bada85600e24ae82c9dee32671ac5038f6842558610f5cf3129f21e", "50b0b26bbfe72cacee2c8e1612e057c1855da8320232965137dd67f9eab77523", "26f990be93e0b8ce5ddbe468d0d242c51686c2ad9608ee44b17875b991e23e82"},
            {"110022003300440055006600770088009900aa00bb00cc00dd00ee00ff", "340b1fe486af2cd6b0ef8786d1c747fc3d785c5499d35faa25fbde57f9bf70ae", "2c5bc7104c059e2db8cda7b500424d2438ae2635b6cddcc51695a11e7ec95cc7", "62bc8969bfb02cac905f0739cecadc456bf48539a9b268b2c2725d3c2680be88"},
            {"110022003300440055006600770088009900aa00bb00cc00dd00ee00ff", "a39e0f59a4d8448ab4a614edb998e3f315d3a4c855855386afdfe664a42a0864", "752066dbf4e862d634440649014ec1fc64fdcea1127320acc09b223b1e7fbc59", "2a613585d5ac1741080380bdf7b151e559ccb37c5521674a77b999c9de0f21e8"},
            {"110022003300440055006600770088009900aa00bb00cc00dd00ee00ff", "df0a60d25576d441cdd76f5a3d63fe1308860121e70e832098abac5b6d5ff715", "65f8ccf807faeb46c0a69d1b1774a53081ec11c5e0ffb854620bcb2f2c8098bb", "32feb22792926929f01d51439814ec1bc414c2ab52b36a7c675e50ea37310b78"},
            {"110022003300440055006600770088009900aa00bb00cc00dd00ee00ff", "79df48383bdf5d295e510f4aadcc3a3b2ed0c3d454e1b1e4315de7c3f1ea86ab", "588ae9380e85c8c2c565321957aa459a4ae0080331d41145b7dea5214bf91377", "7207f2c4969e19ef5de08d088ea7dcd4c1ba67574cc0ac023dc9e7c48abee8b6"},
            {"110022003300440055006600770088009900aa00bb00cc00dd00ee00ff", "44adec39be60bf10994596a0248dcafa78a45ca30472190f0de61523e90ec131", "1bef291f3aa99c551ef5d96d9fd06d945092e185cebe2cda75351e2f27ca3ea2", "2024fda909bfb9b9079f072dbe005ae850a54f16d1b59bc46afc6db2ef5461fe"},
            {"0123456789abcdef00000000000000000123456789abcdef0000000000000000", "e48ae59f8a200c946c827ab8992f8774dc0d5742d50baaa9f26b39a91f471eda", "39af0095c3cd45cba48891f29e2a69c3ba421c97bbc3181aa1f3699e3deb5234", "19ad75c9f49f42648338ddfac0b3597145db0864b897212d3da0a88b796d09f8"},
            {"0123456789abcdef00000000000000000123456789abcdef0000000000000000", "9e1a0043b4e941352f8cd957041244bed7f4a616c254ab8ba1a8ce7fba8a798a", "33b8197cfdefd2d5715139770256037224454ccc21d25bebb91100bcead5aba9", "45857b3c9cea024f663afb580dc284db387627fbe701f620715cdd7bed0ad074"},
            {"0123456789abcdef00000000000000000123456789abcdef0000000000000000", "bda8eafc615336f3db072cb6a77804f284fb89d7ae1ff90c7542278cfb25f1f0", "546033ea22ec13216b202af72d6cc434cdcfad74ef2a745d69adb5a568443118", "45d87e494c695b956fb5f5ec7d7af72ff35642f5b684654dcca72bbbbcf5bfa2"},
            {"0123456789abcdef00000000000000000123456789abcdef0000000000000000", "c8fd5e849b13582c9eb71a98b82fc23597cf85ddeac7f83f1c773be2e73021eb", "3cc7ed384b7f64ed6e50d0ae93617761573571e6b15ae421d957ccb6f7b91d45", "404fd888b72db73a355fe0b5924f699103ae607c70b9f64c40e1667c61e7a80c"},
            {"0123456789abcdef00000000000000000123456789abcdef0000000000000000", "597d869fc78e5ba7eaa27b6dd418d57d90915f9e8212125002187f715510928b", "65f5ae18a30d1c36f4a7af7e75372e47a39e6fdde2ab33156da74bd263eae039", "47fa066b272a29653b9fc8111be55ef5b5de73109fd94211a0484ba9ec29fc59"},
            {"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "abed54431dfcd125916d9c0ebd8a2be9e871300fec5b664cb895acb4bc3e9535", "14aa272497fda7a11be7d3f93c528b5b2decacce8b652a7f07351b6297850586", "51fe6c6165880c91a945601c8b2d161c9f35243989d436b61d0369c2b3281ddd"},
            {"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "ecf4f4f0cc3bcf6d828ccb237a6d80fcf84b55380cd37f8927af2516cde1c4e4", "398e2b26fbb01485d8fcf351249a49023db08db8d89df7cba4b8317df428b3ad", "6c1cb45174d97b6c1197022757155d2d3b33143853d341a2c7054f0f8dfdbaf6"},
            {"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "a85c398d193cd603a4960a90232354baea46918a96c421ab53a972cd7a0892d6", "09269f59ff0423a0289e891cafffbde8fba9eec528f9c27cf4c4670cc1130e4e", "37d383ea040a6d41d7740ddad71b38b160ab61a28bd6f4985c7b0507ac8079c1"},
            {"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "5be2ab78309b5cf85d41d035b42b8c10d7fd69aab5d496af84c0b4760bda639f", "7804f6ca0c17986184affe96ae8df3c163bfa4661fbf3f9208586e30768b99a2", "546c91e23074e3b38b6f4cf9c72b041968f56d06f6bf2e18f03eb02423369d28"},
            {"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "14c2ff5e669fcf329d5a6bafbfd8b2a60b173ea565cdc8f39fc8c8a4564367d3", "5c12e5a06f942446ec0bdd470d13e871acbae8083678b2bcc365e53314d432f7", "3f856d7cac724daa898030dac14835b3702e8f484425e736b51752e6939ddc4d"},
            {"fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "f0f2fd0d2c8af9b1204c8320bf4453d381934afdca9ceaaa2f3fab24783b433a", "0cdfeff3615c787f74c01b0d40a69c75ac7f3e3aa4dd6024c570a19f0af08623", "04a78302a97985b7a7fcfc000d794f885c7bba0631a5f4c86c657e3993434403"},
            {"fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "7defe5ad8b5e636e8667bea1fc19c4619a326091201b3839b65c0339550e8df5", "62513a14aeac3a222b9805d198cf252667eb955348814d922ed33b705fdcaf30", "4f1ec4de4ab1e7ecd942b4a1c23be28a2f108090ee1494a4dd8c56b00560e577"},
            {"fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "c499af12e92bf421a0968aaeb766d140d240bd7b3ed021efc55e69707e95ba09", "78d6ee5e9b9e757671edf8023e38e1f5df9be261a498b9aec8cdaacb7e191b2c", "54448228d3b5e021224847f1706d31e69bc41ada1dc9dde93da0b770f653d8b9"},
            {"fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "22cbd20a30c91f0ad022a185b1e3999fe1c35f64f29572ddd00781c12ea0e024", "74c653169f9296952fec948a92e574365b40a74596913875c6ca0ac2d864a533", "168bed1a949d205b9358326fcffc7ac3318fd21a1c340da727995d3c2642daff"},
            {"fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "9339f4b9ca9ef2c9459462b329261895b5ed2952a1a0d26223fbaea88f60960e", "31b5b2fbaad2e3696cb4492aad8044f264f3ae991dde02c590fe254587d96e0c", "183ae3a641b2056ce425adec1bea9454574a1af367071d9aa28bcdc148ba425d"}
    };

    static Stream<Object[]> generatedTestVectors() {
        return Stream.of(GENERATED_TEST_VECTORS)
                .map(args -> new Object[]{new BigInteger(args[0], 16), Sha256Hash.wrap(args[1]),
                        new BigInteger(args[2], 16), new BigInteger(args[3], 16)});
    }

    @MethodSource("generatedTestVectors")
    @ParameterizedTest(name = "[{index}] privKey={0}, messageHash={1}, expectedR={2}, expectedS={3}")
    public void testSign(BigInteger privKey, Sha256Hash messageHash, BigInteger expectedR, BigInteger expectedS) {
        var key = LowRSigningKey.from(ECKey.fromPrivate(privKey));
        var signature = key.sign(messageHash);

        // Signature is valid and low-R.
        assertTrue(key.verify(messageHash, signature));
        assertTrue(signature.r.bitLength() < 256);

        // Signature matches that produced by Bitcoin Core/Knots with the same message & private key,
        // which shows that the same nonce k was chosen, at least up to a sign (mod N, the curve order).
        assertEquals(expectedR, signature.r);
        assertEquals(expectedS, signature.s);
    }

    @Disabled
    public static class TestVectorGeneration {
        private static final String BITCOIN_TX_COMMAND_FMT_STR = "bitcoin-tx -create " +
                "in=00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff:0 " +
                "set=privatekeys:[\"%s\"] " + (
                "set=prevtxs:[{" +
                        "\"txid\":\"00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff\"," +
                        "\"vout\":0," +
                        "\"amount\":\"0.001\"," +
                        "\"scriptPubKey\":\"%s\"}] ") +
                "outaddr=%s:193P6LtvS4nCnkDvM9uXn1gsSRqh4aDAz7 " +
                "sign=ALL";

        private static final String[] PRIVATE_KEYS = {
                "02", "110022003300440055006600770088009900aa00bb00cc00dd00ee00ff", "0123456789abcdef00000000000000000123456789abcdef0000000000000000",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140" // = -1 (mod N)
        };

        private static final String[] COIN_OUTPUT_AMOUNTS = {
                "0.0001", "0.0003", "0.0005", "0.0007", "0.0009"
        };

        @Test
        public void preparedTestVectorsMatch() {
            assertArrayEquals(GENERATED_TEST_VECTORS, Arrays.stream(PRIVATE_KEYS)
                    .flatMap(privKeyHex -> Arrays.stream(COIN_OUTPUT_AMOUNTS)
                            .map(coinOutputAmount -> assertDoesNotThrow(() -> generateTestVector(privKeyHex, coinOutputAmount))))
//                    .forEach(vector -> System.out.printf("{\"%s\", \"%s\", \"%s\", \"%s\"},%n", (Object[]) vector));
                    .toArray(String[][]::new));
        }

        private static String[] generateTestVector(String privKeyHex, String coinOutputAmount) throws Exception {
            ECKey key = ECKey.fromPrivate(new BigInteger(privKeyHex, 16));
            Script scriptCode = ScriptBuilder.createP2PKHOutputScript(key);
            Script scriptPubKey = ScriptBuilder.createP2WPKHOutputScript(key);
            String scriptPubKeyHex = Utilities.encodeToHex(scriptPubKey.getProgram());
            String wif = key.getPrivateKeyAsWiF(MainNetParams.get());

            String txHex = callBitcoinTx(wif, scriptPubKeyHex, coinOutputAmount);

            var tx = new Transaction(MainNetParams.get(), Utilities.decodeFromHex(txHex));
            var witness = tx.getInput(0).getWitness();
            var sigHash = tx.hashForWitnessSignature(0, scriptCode, Coin.MILLICOIN, Transaction.SigHash.ALL, false);
            var signature = TransactionSignature.decodeFromBitcoin(witness.getPush(0), true, true);

            assertTrue(key.verify(sigHash, signature));
            return new String[]{privKeyHex, sigHash.toString(),
                    Strings.padStart(signature.r.toString(16), 64, '0'),
                    Strings.padStart(signature.s.toString(16), 64, '0')};
        }

        private static String callBitcoinTx(String wif,
                                            String scriptPubKeyHex,
                                            String coinOutputAmount) throws IOException {
            String cmd = String.format(BITCOIN_TX_COMMAND_FMT_STR, wif, scriptPubKeyHex, coinOutputAmount);
            Process process = new ProcessBuilder(cmd.split(" ")).start();
            var outputStream = new ByteArrayOutputStream();
            process.getInputStream().transferTo(outputStream);

            int exitCode = Futures.getUnchecked(process.onExit().thenApply(Process::exitValue));
            String output = outputStream.toString(StandardCharsets.UTF_8).trim();

            assertEquals(0, exitCode);
            return output;
        }
    }
}
