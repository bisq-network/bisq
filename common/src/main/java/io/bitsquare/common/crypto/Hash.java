/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.common.crypto;

import com.google.common.base.Charsets;
import io.bitsquare.common.util.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

public class Hash {
    private static final Logger log = LoggerFactory.getLogger(Hash.class);

    /**
     * @param data Data as byte array
     * @return Hash of data
     */
    public static byte[] getHash(byte[] data) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256", "BC");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Could not create MessageDigest for hash. " + e.getMessage());
            throw new RuntimeException(e);
        }
        digest.update(data, 0, data.length);
        return digest.digest();
    }

    /**
     * @param data Any serializable object. Will be converted into a byte array using Java serialisation.
     * @return Hash of data
     */
    public static byte[] getHash(Serializable data) {
        return getHash(Utilities.serialize(data));
    }

    /**
     * @param message UTF-8 encoded message
     * @return Hash of data
     */
    public static byte[] getHash(String message) {
        return getHash(message.getBytes(Charsets.UTF_8));
    }

    /**
     * @param message UTF-8 encoded message
     * @return Hex string of hash of data
     */
    public static String getHashAsHex(String message) {
        return Hex.toHexString(message.getBytes(Charsets.UTF_8));
    }

    /**
     * @param data data as Integer
     * @return Hash of data
     */
    public static byte[] getHash(Integer data) {
        return getHash(ByteBuffer.allocate(4).putInt(data).array());
    }

}

