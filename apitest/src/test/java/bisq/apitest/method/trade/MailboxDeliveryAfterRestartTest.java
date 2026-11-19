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

package bisq.apitest.method.trade;

import bisq.apitest.dao.DaoTestUtils;

import bisq.proto.grpc.OfferInfo;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static protobuf.OfferDirection.BUY;

/**
 * Fresh-stack test for mailbox-delivery-after-offline. Alice (maker) buys BTC, so she sends
 * the payment-started message. Bob (seller) is stopped at that moment, so the message must
 * go to the mailbox at the seed node, and Bob must receive it when he starts again. The
 * mailbox code changed in #246 and #258. In the compat run Bob is the release, which also
 * covers a message from the new version read by the old one.
 */
@Slf4j
@Tag("freshstack")
public class MailboxDeliveryAfterRestartTest extends DockerTradeTest {

    // container_name of bob in apitest/docker/dao-compose.yml
    private static final String BOB_CONTAINER = "bob";
    private static final long AMOUNT = 1_250_000L;

    @Test
    public void testPaymentStartedReachesSellerThroughMailbox() {
        ensureXmrAccounts();
        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> aliceClient.createFixedPricedOffer(BUY.name(),
                XMR, AMOUNT, AMOUNT, "0.00455500",
                defaultBuyerSecurityDepositPct.get(), alicesXmrAcct.getId(), BSQ));
        mineBlocks(1);
        awaitOfferActivated(offer.getId());

        String tradeId = takeOfferAndConfirmDeposit(offer, bobsXmrAcct.getId(), XMR, AMOUNT).getTradeId();
        awaitCond(() -> aliceClient.getTrade(tradeId).getIsDepositConfirmed(), "alice sees deposit confirmed");

        DaoTestUtils.stopContainer(BOB_CONTAINER);
        try {
            aliceClient.confirmPaymentStarted(tradeId);
            // The state proves the mailbox path: a direct delivery would end in BUYER_SAW_ARRIVED_...
            awaitCond(() -> "BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG"
                            .equals(aliceClient.getTrade(tradeId).getState()),
                    "alice stored payment-started in bob's mailbox");
        } finally {
            DaoTestUtils.startContainer(BOB_CONTAINER);
        }

        chain.awaitRestartedAtTip(bobClient, "bob");
        // Bob's trade can only reach the payment-started phase through Alice's message.
        DaoTestUtils.await(() -> bobClient.getTrade(tradeId).getIsPaymentStartedMessageSent(),
                120_000, "bob received payment-started from his mailbox");

        confirmPaymentReceivedAndClose(tradeId, aliceClient, bobClient);
    }
}
