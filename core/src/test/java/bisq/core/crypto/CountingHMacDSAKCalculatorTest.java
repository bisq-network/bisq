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

import org.bitcoinj.core.Sha256Hash;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CountingHMacDSAKCalculatorTest {
    // Taken from https://www.rfc-editor.org/rfc/rfc6979:
    private static final String[][] RFC_6979_SHA_256_ECDSA_TEST_VECTORS = {
            // NIST P-192
            {"ffffffffffffffffffffffff99def836146bc9b1b4d22831", "6fab034934e4c0fc9ae67f5b5659a9d7d1fefd187ee09fd4", "sample", "32b1b6d7d42a05cb449065727a84804fb1a3e34d8f261496"},
            {"ffffffffffffffffffffffff99def836146bc9b1b4d22831", "6fab034934e4c0fc9ae67f5b5659a9d7d1fefd187ee09fd4", "test", "5c4ce89cf56d9e7c77c8585339b006b97b5f0680b4306c6c"},
            // NIST P-224
            {"ffffffffffffffffffffffffffff16a2e0b8f03e13dd29455c5c2a3d", "f220266e1105bfe3083e03ec7a3a654651f45e37167e88600bf257c1", "sample", "ad3029e0278f80643de33917ce6908c70a8ff50a411f06e41dedfcdc"},
            {"ffffffffffffffffffffffffffff16a2e0b8f03e13dd29455c5c2a3d", "f220266e1105bfe3083e03ec7a3a654651f45e37167e88600bf257c1", "test", "ff86f57924da248d6e44e8154eb69f0ae2aebaee9931d0b5a969f904"},
            // NIST p-256
            {"ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", "c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721", "sample", "a6e3c57dd01abe90086538398355dd4c3b17aa873382b0f24d6129493d8aad60"},
            {"ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", "c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721", "test", "d16b6ae827f17175e040871a1c7ec3500192c4c92677336ec2537acaee0008e0"},
            // NIST P-384
            {"ffffffffffffffffffffffffffffffffffffffffffffffffc7634d81f4372ddf581a0db248b0a77aecec196accc52973", "6b9d3dad2e1b8c1c05b19875b6659f4de23c3b667bf297ba9aa47740787137d896d5724e4c70a825f872c9ea60d2edf5", "sample", "180ae9f9aec5438a44bc159a1fcb277c7be54fa20e7cf404b490650a8acc414e375572342863c899f9f2edf9747a9b60"},
            {"ffffffffffffffffffffffffffffffffffffffffffffffffc7634d81f4372ddf581a0db248b0a77aecec196accc52973", "6b9d3dad2e1b8c1c05b19875b6659f4de23c3b667bf297ba9aa47740787137d896d5724e4c70a825f872c9ea60d2edf5", "test", "0cfac37587532347dc3389fdc98286bba8c73807285b184c83e62e26c401c0faa48dd070ba79921a3457abff2d630ad7"},
            // NIST P-521
            {"1fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffa51868783bf2f966b7fcc0148f709a5d03bb5c9b8899c47aebb6fb71e91386409", "0fad06daa62ba3b25d2fb40133da757205de67f5bb0018fee8c86e1b68c7e75caa896eb32f1f47c70855836a6d16fcc1466f6d8fbec67db89ec0c08b0e996b83538", "sample", "0edf38afcaaecab4383358b34d67c9f2216c8382aaea44a3dad5fdc9c32575761793fef24eb0fc276dfc4f6e3ec476752f043cf01415387470bcbd8678ed2c7e1a0"},
            {"1fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffa51868783bf2f966b7fcc0148f709a5d03bb5c9b8899c47aebb6fb71e91386409", "0fad06daa62ba3b25d2fb40133da757205de67f5bb0018fee8c86e1b68c7e75caa896eb32f1f47c70855836a6d16fcc1466f6d8fbec67db89ec0c08b0e996b83538", "test", "01de74955efaabc4c4f17f8e84d881d1310b5392d7700275f82f145c61e843841af09035bf7a6210f5a431a6a9e81c9323354a9e69135d44ebd2fcaa7731b909258"},
            // NIST K-163
            {"4000000000000000000020108a2e0cc0d99f8a5ef", "09a4d6792295a7f730fc3f2b49cbc0f62e862272f", "sample", "23af4074c90a02b3fe61d286d5c87f425e6bdd81b"},
            {"4000000000000000000020108a2e0cc0d99f8a5ef", "09a4d6792295a7f730fc3f2b49cbc0f62e862272f", "test", "193649ce51f0cff0784cfc47628f4fa854a93f7a2"},
            // NIST K-233
            {"8000000000000000000000000000069d5bb915bcd46efb1ad5f173abdf", "103b2142bdc2a3c3b55080d09df1808f79336da2399f5ca7171d1be9b0", "sample", "73552f9cac5774f74f485fa253871f2109a0c86040552eaa67dba92dc9"},
            {"8000000000000000000000000000069d5bb915bcd46efb1ad5f173abdf", "103b2142bdc2a3c3b55080d09df1808f79336da2399f5ca7171d1be9b0", "test", "2ce5aedc155acc0ddc5e679ebacfd21308362e5efc05c5e99b2557a8d7"},
            // NIST K-283
            {"1ffffffffffffffffffffffffffffffffffe9ae2ed07577265dff7f94451e061e163c61", "06a0777356e87b89ba1ed3a3d845357be332173c8f7a65bdc7db4fab3c4cc79acc8194e", "sample", "1ceb9e8e0dff53ce687deb81339aca3c98e7a657d5a9499ef779f887a934408ecbe5a38"},
            {"1ffffffffffffffffffffffffffffffffffe9ae2ed07577265dff7f94451e061e163c61", "06a0777356e87b89ba1ed3a3d845357be332173c8f7a65bdc7db4fab3c4cc79acc8194e", "test", "0b585a7a68f51089691d6ede2b43fc4451f66c10e65f134b963d4cbd4eb844b0e1469a6"},
            // NIST K-409
            {"7ffffffffffffffffffffffffffffffffffffffffffffffffffe5f83b2d4ea20400ec4557d5ed3e3e7ca5b4b5c83b8e01e5fcf", "29c16768f01d1b8a89fda85e2efd73a09558b92a178a2931f359e4d70ad853e569cdaf16daa569758fb4e73089e4525d8bbfcf", "sample", "782385f18baf5a36a588637a76dfab05739a14163bf723a4417b74bd1469d37ac9e8cce6aec8ff63f37b815aaf14a876eed962"},
            {"7ffffffffffffffffffffffffffffffffffffffffffffffffffe5f83b2d4ea20400ec4557d5ed3e3e7ca5b4b5c83b8e01e5fcf", "29c16768f01d1b8a89fda85e2efd73a09558b92a178a2931f359e4d70ad853e569cdaf16daa569758fb4e73089e4525d8bbfcf", "test", "251e32dee10ed5ea4ad7370df3eff091e467d5531ca59de3aa791763715e1169ab5e18c2a11cd473b0044fb45308e8542f2eb0"},
            // NIST K-571
            {"20000000000000000000000000000000000000000000000000000000000000000000000131850e1f19a63e4b391a8db917f4138b630d84be5d639381e91deb45cfe778f637c1001", "0c16f58550d824ed7b95569d4445375d3a490bc7e0194c41a39deb732c29396cdf1d66de02dd1460a816606f3bec0f32202c7bd18a32d87506466aa92032f1314ed7b19762b0d22", "sample", "0f79d53e63d89fb87f4d9e6dc5949f5d9388bcfe9ebcb4c2f7ce497814cf40e845705f8f18dbf0f860de0b1cc4a433ef74a5741f3202e958c082e0b76e16ecd5866aa0f5f3df300"},
            {"20000000000000000000000000000000000000000000000000000000000000000000000131850e1f19a63e4b391a8db917f4138b630d84be5d639381e91deb45cfe778f637c1001", "0c16f58550d824ed7b95569d4445375d3a490bc7e0194c41a39deb732c29396cdf1d66de02dd1460a816606f3bec0f32202c7bd18a32d87506466aa92032f1314ed7b19762b0d22", "test", "04ddd0707e81bb56ea2d1d45d7fafdbdd56912cae224086802fea1018db306c4fb8d93338dbf6841ce6c6ab1506e9a848d2c0463e0889268843dee4acb552cffcb858784ed116b2"},
            // NIST B-163
            {"40000000000000000000292fe77e70c12a4234c33", "35318fc447d48d7e6bc93b48617dddedf26aa658f", "sample", "3d7086a59e6981064a9cdb684653f3a81b6ec0f0b"},
            {"40000000000000000000292fe77e70c12a4234c33", "35318fc447d48d7e6bc93b48617dddedf26aa658f", "test", "38145e3ffca94e4ddacc20ad6e0997bd0e3b669d2"},
            // NIST B-233
            {"1000000000000000000000000000013e974e72f8a6922031d2603cfe0d7", "07adc13dd5bf34d1ddeeb50b2ce23b5f5e6d18067306d60c5f6ff11e5d3", "sample", "034a53897b0bbdb484302e19bf3f9b34a2abfed639d109a388dc52006b5"},
            {"1000000000000000000000000000013e974e72f8a6922031d2603cfe0d7", "07adc13dd5bf34d1ddeeb50b2ce23b5f5e6d18067306d60c5f6ff11e5d3", "test", "00376886e89013f7ff4b5214d56a30d49c99f53f211a3afe01aa2bde12d"},
            // NIST B-283
            {"3ffffffffffffffffffffffffffffffffffef90399660fc938a90165b042a7cefadb307", "14510d4bc44f2d26f4553942c98073c1bd35545ceabb5cc138853c5158d2729ea408836", "sample", "38c9d662188982943e080b794a4cfb0732dba37c6f40d5b8cfaded6ff31c5452ba3f877"},
            {"3ffffffffffffffffffffffffffffffffffef90399660fc938a90165b042a7cefadb307", "14510d4bc44f2d26f4553942c98073c1bd35545ceabb5cc138853c5158d2729ea408836", "test", "018a7d44f2b4341fefe68f6bd8894960f97e08124aab92c1ffbbe90450fcc9356c9aaa5"},
            // NIST B-409
            {"10000000000000000000000000000000000000000000000000001e2aad6a612f33307be5fa47c3c9e052f838164cd37d9a21173", "0494994cc325b08e7b4ce038bd9436f90b5e59a2c13c3140cd3ae07c04a01fc489f572ce0569a6db7b8060393de76330c624177", "sample", "08ec42d13a3909a20c41bebd2dfed8cacce56c7a7d1251df43f3e9e289dae00e239f6960924ac451e125b784cb687c7f23283fd"},
            {"10000000000000000000000000000000000000000000000000001e2aad6a612f33307be5fa47c3c9e052f838164cd37d9a21173", "0494994cc325b08e7b4ce038bd9436f90b5e59a2c13c3140cd3ae07c04a01fc489f572ce0569a6db7b8060393de76330c624177", "test", "06eba3d58d0e0dfc406d67fc72ef0c943624cf40019d1e48c3b54ccab0594afd5dee30aebaa22e693dbcfecad1a85d774313dad"},
            // NIST B-571
            {"3ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe661ce18ff55987308059b186823851ec7dd9ca1161de93d5174d66e8382e9bb2fe84e47", "028a04857f24c1c082df0d909c0e72f453f2e2340ccb071f0e389bca2575da19124198c57174929ad26e348cf63f78d28021ef5a9bf2d5cbeaf6b7ccb6c4da824dd5c82cfb24e11", "sample", "15c2c6b7d1a070274484774e558b69fdfa193bdb7a23f27c2cd24298ce1b22a6cc9b7fb8cabfd6cf7c6b1cf3251e5a1cddd16fbfed28de79935bb2c631b8b8ea9cc4bcc937e669e"},
            {"3ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe661ce18ff55987308059b186823851ec7dd9ca1161de93d5174d66e8382e9bb2fe84e47", "028a04857f24c1c082df0d909c0e72f453f2e2340ccb071f0e389bca2575da19124198c57174929ad26e348cf63f78d28021ef5a9bf2d5cbeaf6b7ccb6c4da824dd5c82cfb24e11", "test", "328e02cf07c7b5b6d3749d8302f1ae5bfaa8f239398459af4a2c859c7727a8123a7fe9be8b228413fc8dc0e9de16af3f8f43005107f9989a5d97a5c4455da895e81336710a3fb2c"}
    };

    static Stream<Object[]> rfc6979TestVectors() {
        return Stream.of(RFC_6979_SHA_256_ECDSA_TEST_VECTORS)
                .map(args -> new Object[]{new BigInteger(args[0], 16), new BigInteger(args[1], 16), args[2],
                        new BigInteger(args[3], 16)});
    }

    @MethodSource("rfc6979TestVectors")
    @ParameterizedTest(name = "[{index}] n={0}, d={1}, message={2}, expectedK={3}")
    public void testKCalculator(BigInteger n, BigInteger d, String message, BigInteger expectedK) {
        byte[] messageHash = Sha256Hash.hash(message.getBytes(StandardCharsets.UTF_8));
        var kCalculator = new CountingHMacDSAKCalculator();

        // First usage of the k-calculator gives a nonce matching that in the RFC 6979 test vector.
        kCalculator.init(n, d, messageHash);
        assertEquals(expectedK, kCalculator.nextK());

        // Further invocations result in distinct nonces, as the internal counter increments.
        Set<BigInteger> retries = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            kCalculator.init(n, d, messageHash);
            retries.add(kCalculator.nextK());
        }
        retries.add(expectedK);
        assertEquals(5, retries.size());
    }
}
