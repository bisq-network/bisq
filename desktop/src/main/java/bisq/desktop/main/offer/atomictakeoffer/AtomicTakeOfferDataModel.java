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

package bisq.desktop.main.offer.atomictakeoffer;

import bisq.desktop.common.model.ActivatableDataModel;
import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.takeoffer.TakeBsqSwapOfferModel;
import bisq.core.payment.PaymentAccount;
import bisq.core.trade.misc.TradeResultHandler;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import lombok.Getter;

import static com.google.common.base.Preconditions.checkNotNull;

class AtomicTakeOfferDataModel extends ActivatableDataModel {
    private final TakeBsqSwapOfferModel takeBsqSwapOfferModel;
    @Getter
    private final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    private final OfferBook offerBook;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    AtomicTakeOfferDataModel(TakeBsqSwapOfferModel takeBsqSwapOfferModel,
                             OfferBook offerBook
    ) {
        this.takeBsqSwapOfferModel = takeBsqSwapOfferModel;
        this.offerBook = offerBook;
    }

    @Override
    protected void activate() {
        takeBsqSwapOfferModel.activate(errorMessage -> new Popup().warning(errorMessage).show());
    }

    @Override
    protected void deactivate() {
        takeBsqSwapOfferModel.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate
    void initWithData(Offer offer) {
        takeBsqSwapOfferModel.initWithData(offer);
    }

    public void onClose(boolean removeOffer) {
        // We do not wait until the offer got removed by a network remove message but remove it
        // directly from the offer book. The broadcast gets now bundled and has 2 sec. delay so the
        // removal from the network is a bit slower as it has been before. To avoid that the taker gets
        // confused to see the same offer still in the offerbook we remove it manually. This removal has
        // only local effect. Other trader might see the offer for a few seconds
        // still (but cannot take it).
        if (removeOffer) {
            offerBook.removeOffer(checkNotNull(takeBsqSwapOfferModel.getOffer()));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onTakeOffer(TradeResultHandler<BsqSwapTrade> tradeResultHandler) {
        takeBsqSwapOfferModel.onTakeOffer(tradeResultHandler,
                warningMessage -> new Popup().warning(warningMessage).show(),
                errorMessage -> {
                    log.warn(errorMessage);
                    new Popup().warning(errorMessage).show();
                }
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    OfferPayload.Direction getDirection() {
        return takeBsqSwapOfferModel.getDirection();
    }

    public Offer getOffer() {
        return takeBsqSwapOfferModel.getOffer();
    }

    PaymentAccount getPaymentAccount() {
        return takeBsqSwapOfferModel.getPaymentAccount();
    }

    long getMaxTradeLimit() {
        return takeBsqSwapOfferModel.getMaxTradeLimit();
    }

    public BooleanProperty getIsTxBuilderReady() {
        return takeBsqSwapOfferModel.getIsTxBuilderReady();
    }

    ObjectProperty<Volume> getVolume() {
        return takeBsqSwapOfferModel.getVolume();
    }

    Price getTradePrice() {
        return takeBsqSwapOfferModel.getTradePrice();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        takeBsqSwapOfferModel.calculateVolume();
    }

    void applyAmount(Coin amount) {
        takeBsqSwapOfferModel.applyAmount(amount);
    }

    boolean isMinAmountLessOrEqualAmount() {
        return takeBsqSwapOfferModel.isMinAmountLessOrEqualAmount();
    }

    boolean isAmountLargerThanOfferAmount() {
        return takeBsqSwapOfferModel.isAmountLargerThanOfferAmount();
    }

    ReadOnlyObjectProperty<Coin> getAmount() {
        return takeBsqSwapOfferModel.getAmount();
    }

    public String getCurrencyCode() {
        return takeBsqSwapOfferModel.getCurrencyCode();
    }

    public String getCurrencyNameAndCode() {
        return takeBsqSwapOfferModel.getCurrencyNameAndCode();
    }

    public Coin getUsableBsqBalance() {
        return takeBsqSwapOfferModel.getUsableBsqBalance();
    }

    public boolean hasEnoughBtc() {
        return takeBsqSwapOfferModel.hasEnoughBtc();
    }

    public boolean hasEnoughBsq() {
        return takeBsqSwapOfferModel.hasEnoughBsq();
    }
}
