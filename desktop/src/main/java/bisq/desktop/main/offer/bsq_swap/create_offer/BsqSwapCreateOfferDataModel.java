/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation,
either version 3 of the License,
or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful,
but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not,
see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.offer.bsq_swap.create_offer;

import bisq.desktop.main.offer.bsq_swap.BsqSwapOfferDataModel;

import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.bsq_swap.BsqSwapOfferModel;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;
import bisq.core.offer.bsq_swap.OpenBsqSwapOfferService;
import bisq.core.payment.PaymentAccount;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Comparator.comparing;

@Slf4j
class BsqSwapCreateOfferDataModel extends BsqSwapOfferDataModel {
    private final OpenBsqSwapOfferService openBsqSwapOfferService;
    Offer offer;
    private SetChangeListener<PaymentAccount> paymentAccountsChangeListener;
    @Getter
    private final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();
    @Getter
    private PaymentAccount paymentAccount;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    BsqSwapCreateOfferDataModel(BsqSwapOfferModel bsqSwapOfferModel,
                                OpenBsqSwapOfferService openBsqSwapOfferService,
                                User user,
                                P2PService p2PService,
                                @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        super(bsqSwapOfferModel,
                user,
                p2PService,
                btcFormatter);

        this.openBsqSwapOfferService = openBsqSwapOfferService;

        setOfferId(OfferUtil.getRandomOfferId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithData(OfferDirection direction, @Nullable BsqSwapOfferPayload offerPayload) {
        bsqSwapOfferModel.init(direction, true, offerPayload != null ? new Offer(offerPayload) : null);

        fillPaymentAccounts();
        applyPaymentAccount();
        applyTradeCurrency();
    }

    protected void requestNewOffer(Consumer<Offer> resultHandler) {
        openBsqSwapOfferService.requestNewOffer(getOfferId(),
                getDirection(),
                getBtcAmount().get(),
                getMinAmount().get(),
                getPrice().get(),
                offer -> {
                    this.offer = offer;
                    resultHandler.accept(offer);
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onPlaceOffer(Runnable resultHandler) {
        openBsqSwapOfferService.placeBsqSwapOffer(offer,
                resultHandler,
                log::error);
    }

    @Override
    protected void createListeners() {
        super.createListeners();
        paymentAccountsChangeListener = change -> fillPaymentAccounts();
    }

    @Override
    protected void addListeners() {
        super.addListeners();
        user.getPaymentAccountsAsObservable().addListener(paymentAccountsChangeListener);
    }

    @Override
    protected void removeListeners() {
        super.removeListeners();
        user.getPaymentAccountsAsObservable().removeListener(paymentAccountsChangeListener);
    }

    private void fillPaymentAccounts() {
        if (getUserPaymentAccounts() != null) {
            paymentAccounts.setAll(new HashSet<>(getUserPaymentAccounts()));
        }
        paymentAccounts.sort(comparing(PaymentAccount::getAccountName));
    }

    private Set<PaymentAccount> getUserPaymentAccounts() {
        return user.getPaymentAccounts();
    }

    private void applyPaymentAccount() {
        Optional<PaymentAccount> bsqAccountOptional = Objects.requireNonNull(getUserPaymentAccounts()).stream()
                .filter(e -> e.getPaymentMethod().isBsqSwap()).findFirst();
        checkArgument(bsqAccountOptional.isPresent(), "BSQ account must be present");
        this.paymentAccount = bsqAccountOptional.get();
    }

    private void applyTradeCurrency() {
        Optional<TradeCurrency> optionalTradeCurrency = paymentAccount.getTradeCurrency();
        checkArgument(optionalTradeCurrency.isPresent(), "BSQ tradeCurrency must be present");
        tradeCurrency = optionalTradeCurrency.get();
    }
}
