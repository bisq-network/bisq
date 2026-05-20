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

package bisq.apitest.method.offer;

import protobuf.PaymentAccount;
import bisq.proto.grpc.OfferInfo;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.USD;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static bisq.proto.grpc.EditOfferRequest.EditType.ACTIVATION_STATE_ONLY;
import static bisq.proto.grpc.EditOfferRequest.EditType.FIXED_PRICE_AND_ACTIVATION_STATE;
import static bisq.proto.grpc.EditOfferRequest.EditType.FIXED_PRICE_ONLY;
import static bisq.proto.grpc.EditOfferRequest.EditType.TRIGGER_PRICE_ONLY;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferDirection.BUY;
import static protobuf.OfferDirection.SELL;

/**
 * Covers the editOffer gRPC surface around fixed-price offers + activation toggle,
 * plus the platform restrictions for BSQ and BSQ-swap offers. Market-price-margin
 * variants are excluded because this stack has no external price feed.
 */
@Slf4j
public class EditOfferTest extends DockerOfferTest {

    private static final long AMOUNT = 1_250_000L;

    @BeforeAll
    public static void setupAccounts() {
        ensureLegacyBsqAccounts();
        ensureXmrAccounts();
    }

    @Test
    public void testOfferDisableAndEnable() {
        PaymentAccount acct = getOrCreateF2F("US");
        OfferInfo original = createFixedPriced(BUY.name(), USD, acct.getId(), "50000");
        mineBlocks(1);
        awaitOfferActivated(original.getId());
        original = aliceClient.getOffer(original.getId());

        aliceClient.editOfferActivationState(original.getId(), DEACTIVATE_OFFER);
        awaitOffer(original.getId(), o -> !o.getIsActivated(), "offer deactivated");

        aliceClient.editOfferActivationState(original.getId(), ACTIVATE_OFFER);
        awaitOffer(original.getId(), OfferInfo::getIsActivated, "offer re-activated");
        OfferInfo edited = aliceClient.getOffer(original.getId());
        assertTrue(edited.getIsActivated());
        sanityCheck(original, edited);
    }

    @Test
    public void testEditFixedPrice() {
        PaymentAccount acct = getOrCreateF2F("US");
        OfferInfo original = createFixedPriced(BUY.name(), USD, acct.getId(), "50000");
        mineBlocks(1);
        awaitOfferActivated(original.getId());

        aliceClient.editOfferFixedPrice(original.getId(), "55000");
        awaitOffer(original.getId(), o -> "55000.0000".equals(o.getPrice()), "fixed price → 55000");
        OfferInfo edited = aliceClient.getOffer(original.getId());
        assertFalse(edited.getUseMarketBasedPrice());
        sanityCheck(original, edited);
    }

    @Test
    public void testEditFixedPriceAndDeactivate() {
        PaymentAccount acct = getOrCreateF2F("US");
        OfferInfo original = createFixedPriced(BUY.name(), USD, acct.getId(), "50000");
        mineBlocks(1);
        awaitOfferActivated(original.getId());

        aliceClient.editOffer(original.getId(), "60000", false, 0.0,
                NO_TRIGGER_PRICE, DEACTIVATE_OFFER, FIXED_PRICE_AND_ACTIVATION_STATE);
        awaitOffer(original.getId(),
                o -> "60000.0000".equals(o.getPrice()) && !o.getIsActivated(),
                "edit (price=60000, deactivated) applied");
        OfferInfo edited = aliceClient.getOffer(original.getId());
        sanityCheck(original, edited);
    }

    @Test
    public void testBsqOfferRejectsTriggerPrice() {
        OfferInfo original = aliceClient.createFixedPricedOffer(BUY.name(),
                BSQ, AMOUNT, AMOUNT, "0.00005",
                defaultBuyerSecurityDepositPct.get(), alicesLegacyBsqAcct.getId(), BSQ);
        mineBlocks(1);
        awaitOfferActivated(original.getId());
        Throwable ex = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.editOffer(original.getId(), "0.00", false, 0.1d,
                        "0.00005000", ACTIVATE_OFFER, TRIGGER_PRICE_ONLY));
        assertEquals(format("INVALID_ARGUMENT: cannot set mkt price margin or"
                        + " trigger price on fixed price bsq offer with id '%s'", original.getId()),
                ex.getMessage());
    }

    @Test
    public void testEditFixedPriceOnBsqOffer() {
        OfferInfo original = aliceClient.createFixedPricedOffer(BUY.name(),
                BSQ, AMOUNT, AMOUNT, "0.00005",
                defaultBuyerSecurityDepositPct.get(), alicesLegacyBsqAcct.getId(), BSQ);
        mineBlocks(1);
        awaitOfferActivated(original.getId());
        aliceClient.editOffer(original.getId(), "0.00003111", false, 0.0,
                NO_TRIGGER_PRICE, ACTIVATE_OFFER, FIXED_PRICE_ONLY);
        awaitOffer(original.getId(),
                o -> "0.00003111".equals(o.getPrice()) && o.getIsActivated(),
                "bsq fixed price → 0.00003111, activated");
    }

    @Test
    public void testDisableBsqOffer() {
        OfferInfo original = aliceClient.createFixedPricedOffer(BUY.name(),
                BSQ, AMOUNT, AMOUNT, "0.00005000",
                defaultBuyerSecurityDepositPct.get(), alicesLegacyBsqAcct.getId(), BSQ);
        mineBlocks(1);
        awaitOfferActivated(original.getId());
        aliceClient.editOffer(original.getId(), "0.00005000", false, 0.0,
                NO_TRIGGER_PRICE, DEACTIVATE_OFFER, ACTIVATION_STATE_ONLY);
        awaitOffer(original.getId(), o -> !o.getIsActivated(), "bsq offer deactivated");
    }

    @Test
    public void testBsqSwapOfferEditRejected() {
        OfferInfo original = aliceClient.createBsqSwapOffer(SELL.name(),
                1_250_000L, 750_000L, "0.00005");
        assertNotEquals("", original.getId());
        // bsq swap offers are activated immediately on createBsqSwapOffer; no need to await.
        Throwable ex = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.editOffer(original.getId(), "0.000055", false, 0.0,
                        NO_TRIGGER_PRICE, ACTIVATE_OFFER, TRIGGER_PRICE_ONLY));
        assertEquals(format("INVALID_ARGUMENT: cannot edit bsq swap offer with id '%s',"
                        + " replace it with a new swap offer instead", original.getId()),
                ex.getMessage());
    }

    @Test
    public void testEditFixedPriceOnXmrOffer() {
        OfferInfo original = aliceClient.createFixedPricedOffer(BUY.name(),
                XMR, AMOUNT, AMOUNT, "0.008",
                defaultBuyerSecurityDepositPct.get(), alicesXmrAcct.getId(), BSQ);
        mineBlocks(1);
        awaitOfferActivated(original.getId());
        aliceClient.editOffer(original.getId(), "0.00900000", false, 0.0,
                NO_TRIGGER_PRICE, ACTIVATE_OFFER, FIXED_PRICE_ONLY);
        awaitOffer(original.getId(),
                o -> "0.00900000".equals(o.getPrice()) && o.getIsActivated(),
                "xmr fixed price → 0.009, activated");
        OfferInfo edited = aliceClient.getOffer(original.getId());
        assertFalse(edited.getUseMarketBasedPrice());
        sanityCheck(original, edited);
    }

    private OfferInfo createFixedPriced(String dir, String ccy, String acctId, String price) {
        return aliceClient.createFixedPricedOffer(dir, ccy, AMOUNT, AMOUNT, price,
                defaultBuyerSecurityDepositPct.get(), acctId, BSQ);
    }

    private void sanityCheck(OfferInfo original, OfferInfo edited) {
        assertEquals(original.getDirection(), edited.getDirection());
        assertEquals(original.getAmount(), edited.getAmount());
        assertEquals(original.getMinAmount(), edited.getMinAmount());
        assertEquals(original.getTxFee(), edited.getTxFee());
        assertEquals(original.getMakerFee(), edited.getMakerFee());
        assertEquals(original.getPaymentAccountId(), edited.getPaymentAccountId());
        assertEquals(original.getDate(), edited.getDate());
        if (original.getDirection().equals(BUY.name()))
            assertEquals(original.getBuyerSecurityDeposit(), edited.getBuyerSecurityDeposit());
        else
            assertEquals(original.getSellerSecurityDeposit(), edited.getSellerSecurityDeposit());
    }
}
