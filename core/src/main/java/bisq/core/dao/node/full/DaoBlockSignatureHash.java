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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.full;

import bisq.common.crypto.Hash;

import java.io.ByteArrayOutputStream;

import java.nio.charset.StandardCharsets;

public final class DaoBlockSignatureHash {
    private static final byte[] DOMAIN = "Bisq DAO RawBlock signature v1".getBytes(StandardCharsets.UTF_8);

    public static byte[] getHash(RawBlock rawBlock) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.writeBytes(DOMAIN);
        outputStream.writeBytes(rawBlock.encodeCanonical());
        return Hash.getSha256Hash(outputStream.toByteArray());
    }

    private DaoBlockSignatureHash() {
    }
}
