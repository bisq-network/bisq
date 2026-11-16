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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.offer.OfferDirection;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.payment.payload.SepaInstantAccountPayload;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import java.util.List;

/**
 * The contract of a SEPA offer which was taken with a SEPA Instant account, with the payment account data of both
 * traders. It is the one case where the payment methods of the two traders differ, so a check which takes the payment
 * method of the wrong trader or of the offer fails with it.
 */
public final class SepaInstantTakerContractFixture {
    private static final NodeAddress MAKER_NODE_ADDRESS = new NodeAddress("maker.onion", 9999);
    private static final NodeAddress TAKER_NODE_ADDRESS = new NodeAddress("taker.onion", 9999);
    private static final NodeAddress MEDIATOR_NODE_ADDRESS = new NodeAddress("mediator.onion", 9999);
    private static final NodeAddress REFUND_AGENT_NODE_ADDRESS = new NodeAddress("refund.onion", 9999);

    public final PubKeyRing makerPubKeyRing = pubKeyRing();
    public final PubKeyRing takerPubKeyRing = pubKeyRing();
    public final PaymentAccountPayload makerPaymentAccountPayload;
    public final PaymentAccountPayload takerPaymentAccountPayload;
    public final Contract contract;

    public SepaInstantTakerContractFixture(boolean isBuyerMakerAndSellerTaker) {
        SepaAccountPayload sepaAccountPayload = new SepaAccountPayload(PaymentMethod.SEPA_ID,
                "maker-account",
                List.of());
        sepaAccountPayload.setCountryCode("DE");
        sepaAccountPayload.setHolderName("Maker");
        sepaAccountPayload.setIban("DE89370400440532013000");
        sepaAccountPayload.setBic("COBADEFFXXX");
        makerPaymentAccountPayload = sepaAccountPayload;

        SepaInstantAccountPayload sepaInstantAccountPayload = new SepaInstantAccountPayload(PaymentMethod.SEPA_INSTANT_ID,
                "taker-account",
                List.of());
        sepaInstantAccountPayload.setCountryCode("FR");
        sepaInstantAccountPayload.setHolderName("Taker");
        sepaInstantAccountPayload.setIban("FR1420041010050500013M02606");
        sepaInstantAccountPayload.setBic("PSSTFRPPXXX");
        takerPaymentAccountPayload = sepaInstantAccountPayload;

        NodeAddress buyerNodeAddress = isBuyerMakerAndSellerTaker ? MAKER_NODE_ADDRESS : TAKER_NODE_ADDRESS;
        NodeAddress sellerNodeAddress = isBuyerMakerAndSellerTaker ? TAKER_NODE_ADDRESS : MAKER_NODE_ADDRESS;
        contract = new Contract(
                offerPayload(isBuyerMakerAndSellerTaker ? OfferDirection.BUY : OfferDirection.SELL),
                1_000_000L,
                50_000_000L,
                "takerFeeTxId",
                buyerNodeAddress,
                sellerNodeAddress,
                MEDIATOR_NODE_ADDRESS,
                isBuyerMakerAndSellerTaker,
                "makerAccountId",
                "takerAccountId",
                null,
                null,
                makerPubKeyRing,
                takerPubKeyRing,
                "makerPayoutAddress",
                "takerPayoutAddress",
                new byte[33],
                new byte[33],
                0,
                REFUND_AGENT_NODE_ADDRESS,
                makerPaymentAccountPayload.getHashForContract(),
                takerPaymentAccountPayload.getHashForContract(),
                makerPaymentAccountPayload.getPaymentMethodId(),
                takerPaymentAccountPayload.getPaymentMethodId(),
                0,
                pubKeyRing(),
                pubKeyRing());
    }

    private OfferPayload offerPayload(OfferDirection direction) {
        return new OfferPayload("offer-id",
                1_700_000_000_000L,
                MAKER_NODE_ADDRESS,
                makerPubKeyRing,
                direction,
                50_000_000L,
                0,
                false,
                1_000_000L,
                1_000_000L,
                "BTC",
                "EUR",
                List.of(),
                List.of(MEDIATOR_NODE_ADDRESS),
                PaymentMethod.SEPA_ID,
                "makerPaymentAccountId",
                "offerFeePaymentTxId",
                null,
                null,
                null,
                null,
                "1.9.9",
                123_456L,
                1_000L,
                2_000L,
                true,
                3_000L,
                4_000L,
                5_000L,
                6_000L,
                false,
                false,
                0,
                0,
                false,
                null,
                null,
                4);
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(),
                Encryption.generateKeyPair().getPublic());
    }
}
