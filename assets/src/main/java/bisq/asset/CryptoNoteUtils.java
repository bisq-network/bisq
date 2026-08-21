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

package bisq.asset;

import org.bitcoinj.core.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.Map;

public class CryptoNoteUtils {
    public static String getRawSpendKeyAndViewKey(String address) throws CryptoNoteUtils.CryptoNoteException {
        try {
            // See https://monerodocs.org/public-address/standard-address/
            byte[] decoded = CryptoNoteUtils.MoneroBase58.decode(address);
            // Standard addresses are of length 69 and addresses with integrated payment ID of length 77.

            if (decoded.length <= 65) {
                throw new CryptoNoteUtils.CryptoNoteException("The address we received is too short. address=" + address);
            }

            // If the length is not as expected but still can be truncated we log an error and continue.
            if (decoded.length != 69 && decoded.length != 77) {
                System.out.println("The address we received is not in the expected format. address=" + address);
            }

            // We remove the network type byte, the checksum (4 bytes) and optionally the payment ID (8 bytes if present)
            // So extract the 64 bytes after the first byte, which are the 32 byte public spend key + the 32 byte public view key
            byte[] slice = Arrays.copyOfRange(decoded, 1, 65);
            return Utils.HEX.encode(slice);
        } catch (CryptoNoteUtils.CryptoNoteException e) {
            throw new CryptoNoteUtils.CryptoNoteException(e);
        }
    }

    public static class CryptoNoteException extends Exception {
        CryptoNoteException(String msg) {
            super(msg);
        }

        public CryptoNoteException(CryptoNoteException exception) {
            super(exception);
        }
    }

    static class Keccak {
        private static final int BLOCK_SIZE = 136;
        private static final int LONGS_PER_BLOCK = BLOCK_SIZE / 8;
        private static final int KECCAK_ROUNDS = 24;
        private static final long[] KECCAKF_RNDC = {
                0x0000000000000001L, 0x0000000000008082L, 0x800000000000808aL,
                0x8000000080008000L, 0x000000000000808bL, 0x0000000080000001L,
                0x8000000080008081L, 0x8000000000008009L, 0x000000000000008aL,
                0x0000000000000088L, 0x0000000080008009L, 0x000000008000000aL,
                0x000000008000808bL, 0x800000000000008bL, 0x8000000000008089L,
                0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
                0x000000000000800aL, 0x800000008000000aL, 0x8000000080008081L,
                0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
        };
        private static final int[] KECCAKF_ROTC = {
                1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14,
                27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44
        };
        private static final int[] KECCAKF_PILN = {
                10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4,
                15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1
        };

        private static long rotateLeft(long value, int shift) {
            return (value << shift) | (value >>> (64 - shift));
        }

        private static void keccakf(long[] st, int rounds) {
            long[] bc = new long[5];

            for (int round = 0; round < rounds; ++round) {
                for (int i = 0; i < 5; ++i) {
                    bc[i] = st[i] ^ st[i + 5] ^ st[i + 10] ^ st[i + 15] ^ st[i + 20];
                }

                for (int i = 0; i < 5; i++) {
                    long t = bc[(i + 4) % 5] ^ rotateLeft(bc[(i + 1) % 5], 1);
                    for (int j = 0; j < 25; j += 5) {
                        st[j + i] ^= t;
                    }
                }

                long t = st[1];
                for (int i = 0; i < 24; ++i) {
                    int j = KECCAKF_PILN[i];
                    bc[0] = st[j];
                    st[j] = rotateLeft(t, KECCAKF_ROTC[i]);
                    t = bc[0];
                }

                for (int j = 0; j < 25; j += 5) {
                    for (int i = 0; i < 5; i++) {
                        bc[i] = st[j + i];
                    }
                    for (int i = 0; i < 5; i++) {
                        st[j + i] ^= (~bc[(i + 1) % 5]) & bc[(i + 2) % 5];
                    }
                }

                st[0] ^= KECCAKF_RNDC[round];
            }
        }

        static ByteBuffer keccak1600(ByteBuffer input) {
            input.order(ByteOrder.LITTLE_ENDIAN);

            int fullBlocks = input.remaining() / BLOCK_SIZE;
            long[] st = new long[25];
            for (int block = 0; block < fullBlocks; ++block) {
                for (int index = 0; index < LONGS_PER_BLOCK; ++index) {
                    st[index] ^= input.getLong();
                }
                keccakf(st, KECCAK_ROUNDS);
            }

            ByteBuffer lastBlock = ByteBuffer.allocate(144).order(ByteOrder.LITTLE_ENDIAN);
            lastBlock.put(input);
            lastBlock.put((byte) 1);
            int paddingOffset = BLOCK_SIZE - 1;
            lastBlock.put(paddingOffset, (byte) (lastBlock.get(paddingOffset) | 0x80));
            lastBlock.rewind();

            for (int index = 0; index < LONGS_PER_BLOCK; ++index) {
                st[index] ^= lastBlock.getLong();
            }

            keccakf(st, KECCAK_ROUNDS);

            ByteBuffer result = ByteBuffer.allocate(32);
            result.slice().order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().put(st, 0, 4);
            return result;
        }
    }

    static class MoneroBase58 {

        private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        private static final BigInteger ALPHABET_SIZE = BigInteger.valueOf(ALPHABET.length());
        private static final int FULL_DECODED_BLOCK_SIZE = 8;
        private static final int FULL_ENCODED_BLOCK_SIZE = 11;
        private static final BigInteger UINT64_MAX = new BigInteger("18446744073709551615");
        private static final Map<Integer, Integer> DECODED_CHUNK_LENGTH = Map.of(2, 1,
                3, 2,
                5, 3,
                6, 4,
                7, 5,
                9, 6,
                10, 7,
                11, 8);

        private static void decodeChunk(String input,
                                        int inputOffset,
                                        int inputLength,
                                        byte[] decoded,
                                        int decodedOffset,
                                        int decodedLength) throws CryptoNoteException {

            BigInteger result = BigInteger.ZERO;

            BigInteger order = BigInteger.ONE;
            for (int index = inputOffset + inputLength; index != inputOffset; order = order.multiply(ALPHABET_SIZE)) {
                char character = input.charAt(--index);
                int digit = ALPHABET.indexOf(character);
                if (digit == -1) {
                    throw new CryptoNoteException("invalid character " + character);
                }
                result = result.add(order.multiply(BigInteger.valueOf(digit)));
                if (result.compareTo(UINT64_MAX) > 0) {
                    throw new CryptoNoteException("64-bit unsigned integer overflow " + result.toString());
                }
            }

            BigInteger maxCapacity = BigInteger.ONE.shiftLeft(8 * decodedLength);
            if (result.compareTo(maxCapacity) >= 0) {
                throw new CryptoNoteException("capacity overflow " + result.toString());
            }

            for (int index = decodedOffset + decodedLength; index != decodedOffset; result = result.shiftRight(8)) {
                decoded[--index] = result.byteValue();
            }
        }

        public static byte[] decode(String input) throws CryptoNoteException {
            if (input.length() == 0) {
                return new byte[0];
            }

            int chunks = input.length() / FULL_ENCODED_BLOCK_SIZE;
            int lastEncodedSize = input.length() % FULL_ENCODED_BLOCK_SIZE;
            int lastChunkSize = lastEncodedSize > 0 ? DECODED_CHUNK_LENGTH.get(lastEncodedSize) : 0;

            byte[] result = new byte[chunks * FULL_DECODED_BLOCK_SIZE + lastChunkSize];
            int inputOffset = 0;
            int resultOffset = 0;
            for (int chunk = 0; chunk < chunks; ++chunk,
                    inputOffset += FULL_ENCODED_BLOCK_SIZE,
                    resultOffset += FULL_DECODED_BLOCK_SIZE) {
                decodeChunk(input, inputOffset, FULL_ENCODED_BLOCK_SIZE, result, resultOffset, FULL_DECODED_BLOCK_SIZE);
            }
            if (lastChunkSize > 0) {
                decodeChunk(input, inputOffset, lastEncodedSize, result, resultOffset, lastChunkSize);
            }

            return result;
        }

        private static long readVarInt(ByteBuffer buffer) {
            long result = 0;
            for (int shift = 0; ; shift += 7) {
                byte current = buffer.get();
                result += (current & 0x7fL) << shift;
                if ((current & 0x80L) == 0) {
                    break;
                }
            }
            return result;
        }

        static long decodeAddress(String address, boolean validateChecksum) throws CryptoNoteException {
            byte[] decoded = decode(address);

            int checksumSize = 4;
            if (decoded.length < checksumSize) {
                throw new CryptoNoteException("invalid length");
            }

            ByteBuffer decodedAddress = ByteBuffer.wrap(decoded, 0, decoded.length - checksumSize);

            long prefix = readVarInt(decodedAddress.slice());
            if (!validateChecksum) {
                return prefix;
            }

            ByteBuffer fastHash = Keccak.keccak1600(decodedAddress.slice());
            int checksum = fastHash.getInt();
            int expected = ByteBuffer.wrap(decoded, decoded.length - checksumSize, checksumSize).getInt();
            if (checksum != expected) {
                throw new CryptoNoteException(String.format("invalid checksum %08X, expected %08X", checksum, expected));
            }

            return prefix;
        }
    }
}

