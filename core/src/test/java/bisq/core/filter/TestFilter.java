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

package bisq.core.filter;

import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import org.bouncycastle.util.encoders.Base64;

import java.security.PublicKey;

import java.time.Clock;

import java.nio.charset.StandardCharsets;

import java.util.Collections;
import java.util.List;

import static org.bitcoinj.core.Utils.HEX;

public class TestFilter {

    public static ProtectedStorageEntry createProtectedStorageEntryForFilter(Filter filter) {
        return new ProtectedStorageEntry(
                filter,
                filter.getOwnerPubKey(),
                1000,
                new byte[100],
                Clock.systemDefaultZone()
        );
    }

    public static Filter createSignedFilter(PublicKey ownerPublicKey, ECKey signerKey) {
        return createSignedFilter(ownerPublicKey, signerKey, System.currentTimeMillis());
    }

    public static Filter createSignedFilter(PublicKey ownerPublicKey, ECKey signerKey, long creationDate) {
        Filter unsignedFilter = createFilter(ownerPublicKey, HEX.encode(signerKey.getPubKey()), creationDate);
        return signFilter(unsignedFilter, signerKey);
    }

    public static Filter createFilter(PublicKey ownerPublicKey, String signerPubKeyAsHex) {
        return createFilter(ownerPublicKey, signerPubKeyAsHex, System.currentTimeMillis());
    }

    public static Filter createFilter(PublicKey ownerPublicKey, String signerPubKeyAsHex, long creationDate) {
        return createFilter(ownerPublicKey, signerPubKeyAsHex, creationDate, Collections.emptyList());
    }

    public static Filter createFilter(PublicKey ownerPublicKey, String signerPubKeyAsHex,
                                      long creationDate, List<String> bannedDevKeys) {
        return new Filter(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                Collections.emptyList(),
                false,
                "",
                "",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                ownerPublicKey.getEncoded(),
                creationDate,
                null,
                null,
                signerPubKeyAsHex,
                bannedDevKeys,
                false,
                Collections.emptyList(),
                Collections.emptySet(),
                false,
                false,
                false,
                1,
                Collections.emptyList(),
                1,
                1,
                1,
                1,
                Collections.emptyList(),
                List.of("test1.onion:1221"),
                List.of("test2.onion:1221")
        );
    }

    public static Filter signFilter(Filter unsignedFilter, ECKey signerKey) {
        byte[] filterData = unsignedFilter.serializeForHash();
        Sha256Hash hash = Sha256Hash.of(filterData);

        ECKey.ECDSASignature ecdsaSignature = signerKey.sign(hash);
        byte[] encodeToDER = ecdsaSignature.encodeToDER();

        String signatureAsBase64 = new String(Base64.encode(encodeToDER), StandardCharsets.UTF_8);
        return Filter.cloneWithSig(unsignedFilter, signatureAsBase64);
    }
}
