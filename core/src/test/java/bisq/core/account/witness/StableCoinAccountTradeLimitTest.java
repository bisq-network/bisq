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
package bisq.core.account.witness;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferRestrictions;
import bisq.core.payment.ChargeBackRisk;
import bisq.core.payment.ClearXchangeAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountFactory;
import bisq.core.payment.StableCoinAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.Preferences;

import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Coin;

import java.security.KeyPair;

import java.time.Clock;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// A stablecoin payment method takes the fiat trade path, where the trade limit of a buyer depends on the age and the
// signing of the account for payment methods with chargeback risk. A transfer on a blockchain has no such risk.
public class StableCoinAccountTradeLimitTest {
    private AccountAgeWitnessService service;

    @BeforeEach
    public void setUp() {
        KeyRing keyRing = mock(KeyRing.class);
        PubKeyRing pubKeyRing = mock(PubKeyRing.class);
        when(pubKeyRing.getSignaturePubKeyBytes()).thenReturn(new byte[]{1, 2, 3});
        when(keyRing.getPubKeyRing()).thenReturn(pubKeyRing);
        Preferences preferences = mock(Preferences.class);
        when(preferences.getUserDefinedTradeLimit()).thenReturn(Long.MAX_VALUE);
        service = new AccountAgeWitnessService(keyRing,
                null,
                null,
                mock(SignedWitnessService.class),
                mock(ChargeBackRisk.class),
                null,
                mock(AppendOnlyDataStoreService.class),
                Clock.systemUTC(),
                preferences,
                mock(FilterPolicyService.class));
    }

    @Test
    public void newStableCoinAccountHasTheFullTradeLimitInBothDirections() {
        PaymentMethod paymentMethod = PaymentMethod.getPaymentMethod("USDT_TRC20");
        PaymentAccount account = PaymentAccountFactory.getPaymentAccount(paymentMethod);
        account.init();
        long maxTradeLimit = paymentMethod.getMaxTradeLimitAsCoin("USD").value;
        assertTrue(maxTradeLimit > OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value);

        assertEquals(maxTradeLimit, service.getMyTradeLimit(account, "USD", OfferDirection.BUY));
        assertEquals(maxTradeLimit, service.getMyTradeLimit(account, "USD", OfferDirection.SELL));
    }

    // Shows that the limit by account age applies in this setup, so the test above does not pass for another reason
    @Test
    public void newAccountOfAPaymentMethodWithChargebackRiskIsLimitedForBuying() {
        PaymentAccount account = new ClearXchangeAccount();
        account.init();

        assertEquals(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value,
                service.getMyTradeLimit(account, "USD", OfferDirection.BUY));
    }

    // A taker of a stablecoin offer does not wait for the maker's witness. In the trade the peers verify the witness
    // of the revealed account, and if it is not stored yet they derive it from the account. That must not limit the
    // trade, or a taker would enter a trade which fails later.
    @Test
    public void peerWhoseWitnessIsNotStoredPassesTheTradeCheckWithTheFullLimit() throws Exception {
        PaymentMethod paymentMethod = PaymentMethod.getPaymentMethod("USDT_TRC20");
        StableCoinAccount peersAccount = (StableCoinAccount) PaymentAccountFactory.getPaymentAccount(paymentMethod);
        peersAccount.init();
        peersAccount.setAddress("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
        Coin fullLimit = paymentMethod.getMaxTradeLimitAsCoin("USD");

        assertTrue(verifyPeersWitnessAtTradeDate(peersAccount.getPaymentAccountPayload(), paymentMethod, fullLimit));
    }

    // Shows that the check limits a peer whose witness is not stored where the age matters
    @Test
    public void peerWhoseWitnessIsNotStoredIsLimitedForAPaymentMethodWithChargebackRisk() throws Exception {
        PaymentMethod paymentMethod = PaymentMethod.getPaymentMethod(PaymentMethod.CLEAR_X_CHANGE_ID);
        ClearXchangeAccount peersAccount = new ClearXchangeAccount();
        peersAccount.init();
        peersAccount.setEmailOrMobileNr("peer@example.com");
        Coin fullLimit = paymentMethod.getMaxTradeLimitAsCoin("USD");
        assertTrue(fullLimit.isGreaterThan(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT));

        assertFalse(verifyPeersWitnessAtTradeDate(peersAccount.getPaymentAccountPayload(), paymentMethod, fullLimit));
    }

    // The peer buys BTC, the direction in which the age matters for a payment method with chargeback risk
    private boolean verifyPeersWitnessAtTradeDate(PaymentAccountPayload peersPaymentAccountPayload,
                                                  PaymentMethod paymentMethod,
                                                  Coin tradeAmount) throws Exception {
        KeyPair peersSignatureKeyPair = Sig.generateKeyPair();
        PubKeyRing peersPubKeyRing = new PubKeyRing(peersSignatureKeyPair.getPublic(),
                Encryption.generateKeyPair().getPublic());
        byte[] nonce = {1, 2, 3, 4};
        byte[] signature = Sig.sign(peersSignatureKeyPair.getPrivate(), nonce);
        Offer offer = mock(Offer.class);
        when(offer.getPaymentMethod()).thenReturn(paymentMethod);
        when(offer.getCurrencyCode()).thenReturn("USD");
        when(offer.getDirection()).thenReturn(OfferDirection.BUY);
        Trade trade = mock(Trade.class);
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getAmount()).thenReturn(tradeAmount);

        return service.verifyAccountAgeWitnessAtTradeDate(trade,
                peersPaymentAccountPayload,
                new Date(),
                peersPubKeyRing,
                nonce,
                signature,
                errorMessage -> {
                });
    }
}
