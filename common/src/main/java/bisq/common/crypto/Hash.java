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

package bisq.common.crypto;

import org.bitcoinj.core.Utils;

import com.google.common.base.Charsets;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.nio.ByteBuffer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Hash {

    /**
     * @param data Data as byte array
     * @return Hash of data
     */
    public static byte[] getSha256Hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data, 0, data.length);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            log.error("Could not create MessageDigest for hash. ", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * @param message UTF-8 encoded message
     * @return Hash of data
     */
    public static byte[] getSha256Hash(String message) {
        return getSha256Hash(message.getBytes(Charsets.UTF_8));
    }

    /**
     * @param data data as Integer
     * @return Hash of data
     */
    public static byte[] getSha256Hash(Integer data) {
        return getSha256Hash(ByteBuffer.allocate(4).putInt(data).array());
    }

    /**
     * Calculates RIPEMD160(SHA256(data)).
     */
    public static byte[] getSha256Ripemd160hash(byte[] data) {
        return Utils.sha256hash160(data);
    }

    /**
     * Calculates RIPEMD160(data).
     */
    public static byte[] getRipemd160hash(byte[] data) {
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(data, 0, data.length);
        byte[] out = new byte[20];
        digest.doFinal(out, 0);
        return out;
    }
}

