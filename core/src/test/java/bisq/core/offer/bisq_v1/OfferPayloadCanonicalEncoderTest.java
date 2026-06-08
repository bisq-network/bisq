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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.offer.bisq_v1;

import bisq.core.offer.OfferDirection;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.ProofOfWork;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.encoding.canonical.CanonicalEncoder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.ACCOUNT_AGE_WITNESS_HASH;
import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.CAPABILITIES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class OfferPayloadCanonicalEncoderTest {
    @Test
    public void offerPayloadEncodeCanonicalMatchesStoragePayloadProtobuf() {
        OfferPayload offerPayload = getOfferPayload();

        assertArrayEquals(offerPayload.serialize(),
                offerPayload.encodeCanonical(CanonicalEncoder.DEFAULT));
        assertArrayEquals(offerPayload.encodeCanonical(CanonicalEncoder.DEFAULT),
                offerPayload.serializeForHash());
    }

    @Test
    public void bsqSwapOfferPayloadEncodeCanonicalMatchesStoragePayloadProtobuf() {
        BsqSwapOfferPayload offerPayload = new BsqSwapOfferPayload("bsq-swap-offer-id",
                1_700_000_000_000L,
                new NodeAddress("maker2.onion", 9999),
                getPubKeyRing(),
                OfferDirection.BUY,
                25_000_000L,
                2_000_000L,
                1_000_000L,
                new ProofOfWork(new byte[]{0x01},
                        2L,
                        new byte[]{0x03},
                        -0.0,
                        4L,
                        new byte[]{0x05},
                        1),
                "1.9.9",
                4);

        assertArrayEquals(offerPayload.serialize(),
                offerPayload.encodeCanonical(CanonicalEncoder.DEFAULT));
        assertArrayEquals(offerPayload.encodeCanonical(CanonicalEncoder.DEFAULT),
                offerPayload.serializeForHash());
    }

    private static OfferPayload getOfferPayload() {
        OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap();
        extraDataMap.put(ACCOUNT_AGE_WITNESS_HASH, "age-witness-hash");
        extraDataMap.put(CAPABILITIES, "11,12");

        return new OfferPayload("offer-id",
                1_700_000_000_000L,
                new NodeAddress("maker.onion", 9999),
                getPubKeyRing(),
                OfferDirection.SELL,
                50_000_000L,
                -0.0,
                true,
                2_000_000L,
                1_000_000L,
                "BTC",
                "EUR",
                List.of(new NodeAddress("arbitrator.onion", 9999)),
                List.of(new NodeAddress("mediator.onion", 9999)),
                "SEPA",
                "maker-payment-account-id",
                "offer-fee-payment-tx-id",
                "DE",
                List.of("DE", "AT"),
                "bank-id",
                List.of("bank-a", "bank-b"),
                "1.9.9",
                123_456L,
                1_000L,
                2_000L,
                true,
                3_000L,
                4_000L,
                5_000L,
                6_000L,
                true,
                true,
                40_000_000L,
                60_000_000L,
                true,
                "challenge-hash",
                extraDataMap,
                4);
    }

    private static PubKeyRing getPubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
    }
}
