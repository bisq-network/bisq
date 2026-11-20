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

package bisq.desktop.main.offer.bisq_v1.takeoffer;

import bisq.desktop.Navigation;
import bisq.desktop.main.offer.offerbook.OfferBook;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.filter.FilterPolicyService;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferUtil;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import bisq.common.handlers.ErrorMessageHandler;

import org.bitcoinj.core.Coin;

import javafx.collections.FXCollections;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static bisq.desktop.maker.OfferMaker.btcUsdOffer;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TakeOfferDataModelTest {
    private TradeManager tradeManager;
    private FilterPolicyService filterPolicyService;
    private ErrorMessageHandler refusalHandler;
    private Offer offer;
    private TakeOfferDataModel dataModel;

    @BeforeEach
    void setUp() {
        Res.setup();
        tradeManager = mock(TradeManager.class);
        filterPolicyService = mock(FilterPolicyService.class);
        refusalHandler = mock(ErrorMessageHandler.class);
        offer = make(btcUsdOffer);

        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        when(btcWalletService.getOrCreateAddressEntry(anyString(), any())).thenReturn(mock(AddressEntry.class));
        FeeService feeService = mock(FeeService.class);
        when(feeService.getTxFeePerVbyte()).thenReturn(Coin.valueOf(10));
        AccountAgeWitnessService accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        when(accountAgeWitnessService.getMyTradeLimit(any(), any(), any())).thenReturn(offer.getAmount().value);
        PaymentAccount paymentAccount = mock(PaymentAccount.class);
        when(paymentAccount.getId()).thenReturn("paymentAccountId");

        dataModel = spy(new TakeOfferDataModel(tradeManager,
                mock(OfferBook.class),
                mock(OfferUtil.class),
                btcWalletService,
                mock(BsqWalletService.class),
                mock(User.class),
                feeService,
                mock(MempoolService.class),
                filterPolicyService,
                mock(Preferences.class),
                mock(TxFeeEstimationService.class),
                mock(PriceFeedService.class),
                accountAgeWitnessService,
                mock(Navigation.class),
                mock(P2PService.class)));
        doReturn(FXCollections.observableArrayList(paymentAccount)).when(dataModel).getPossiblePaymentAccounts();
        dataModel.initWithData(offer);
    }

    // The checks before the request end the take offer attempt like the other refusals before a trade exists
    @ParameterizedTest
    @ValueSource(strings = {
            "offerbook.warning.currencyBanned",
            "offerbook.warning.paymentMethodBanned",
            "offerbook.warning.offerBlocked",
            "offerbook.warning.nodeBlocked",
            "offerbook.warning.requireUpdateToNewVersion",
            "offerbook.warning.offerWasAlreadyUsedInTrade"
    })
    void refusalBeforeTheRequestGoesToTheHandlerAndNotToTheTradeManager(String refusalKey) {
        switch (refusalKey) {
            case "offerbook.warning.currencyBanned" ->
                    when(filterPolicyService.isCurrencyBanned(offer.getCurrencyCode())).thenReturn(true);
            case "offerbook.warning.paymentMethodBanned" ->
                    when(filterPolicyService.isPaymentMethodBanned(offer.getPaymentMethod())).thenReturn(true);
            case "offerbook.warning.offerBlocked" ->
                    when(filterPolicyService.isOfferIdBanned(offer.getId())).thenReturn(true);
            case "offerbook.warning.nodeBlocked" ->
                    when(filterPolicyService.isNodeAddressBanned(offer.getMakerNodeAddress())).thenReturn(true);
            case "offerbook.warning.requireUpdateToNewVersion" ->
                    when(filterPolicyService.requireUpdateToNewVersionForTrading()).thenReturn(true);
            default -> when(tradeManager.wasOfferAlreadyUsedInTrade(offer.getId())).thenReturn(true);
        }

        dataModel.onTakeOffer(trade -> {
        }, refusalHandler);

        verify(refusalHandler).handleErrorMessage(Res.get(refusalKey));
        verify(tradeManager, never()).onTakeOffer(any(), any(), any(), anyBoolean(), anyLong(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any());
    }

    // A refusal that the offer state reports, such as an offline maker, is shown through the offer state alone
    @Test
    void tradeManagerRefusalGoesToTheHandlerUnlessTheOfferStateReportsIt() {
        offer.setState(Offer.State.AVAILABLE);
        dataModel.onTakeOffer(trade -> {
        }, refusalHandler);
        ArgumentCaptor<ErrorMessageHandler> tradeManagerHandler = ArgumentCaptor.forClass(ErrorMessageHandler.class);
        // The fee the desktop states before the funding screen: 10 sat/vbyte * (192 + 233) / 2
        verify(tradeManager).onTakeOffer(eq(offer.getAmount()), eq(Coin.valueOf(2_120)), any(), anyBoolean(),
                anyLong(), any(), eq(offer), eq("paymentAccountId"), anyBoolean(), eq(false), any(),
                tradeManagerHandler.capture());

        offer.setState(Offer.State.MAKER_OFFLINE);
        tradeManagerHandler.getValue().handleErrorMessage("reported by the offer state");
        verify(refusalHandler, never()).handleErrorMessage(anyString());

        offer.setState(Offer.State.AVAILABLE);
        tradeManagerHandler.getValue().handleErrorMessage("refused");
        verify(refusalHandler).handleErrorMessage("refused");
    }
}
