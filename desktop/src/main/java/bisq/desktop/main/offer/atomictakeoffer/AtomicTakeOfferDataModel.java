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
import bisq.core.offer.takeoffer.AtomicTakeOfferModel;
import bisq.core.payment.PaymentAccount;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.handlers.TradeResultHandler;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import lombok.Getter;

import static com.google.common.base.Preconditions.checkNotNull;

class AtomicTakeOfferDataModel extends ActivatableDataModel {
    private final AtomicTakeOfferModel atomicTakeOfferModel;
    @Getter
    private final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    private final OfferBook offerBook;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    AtomicTakeOfferDataModel(AtomicTakeOfferModel atomicTakeOfferModel,
                             OfferBook offerBook
    ) {
        this.atomicTakeOfferModel = atomicTakeOfferModel;
        this.offerBook = offerBook;
    }

    @Override
    protected void activate() {
        atomicTakeOfferModel.activate(errorMessage -> new Popup().warning(errorMessage).show());
    }

    @Override
    protected void deactivate() {
        atomicTakeOfferModel.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // called before activate
    void initWithData(Offer offer) {
        atomicTakeOfferModel.initWithData(offer);
    }

    public void onClose(boolean removeOffer) {
        // We do not wait until the offer got removed by a network remove message but remove it
        // directly from the offer book. The broadcast gets now bundled and has 2 sec. delay so the
        // removal from the network is a bit slower as it has been before. To avoid that the taker gets
        // confused to see the same offer still in the offerbook we remove it manually. This removal has
        // only local effect. Other trader might see the offer for a few seconds
        // still (but cannot take it).
        if (removeOffer) {
            offerBook.removeOffer(checkNotNull(atomicTakeOfferModel.getOffer()));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onTakeOffer(TradeResultHandler<AtomicTrade> tradeResultHandler) {
        atomicTakeOfferModel.onTakeOffer(tradeResultHandler,
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
        return atomicTakeOfferModel.getDirection();
    }

    public Offer getOffer() {
        return atomicTakeOfferModel.getOffer();
    }

    PaymentAccount getPaymentAccount() {
        return atomicTakeOfferModel.getPaymentAccount();
    }

    long getMaxTradeLimit() {
        return atomicTakeOfferModel.getMaxTradeLimit();
    }

    public BooleanProperty getIsTxBuilderReady() {
        return atomicTakeOfferModel.getIsTxBuilderReady();
    }

    ObjectProperty<Volume> getVolume() {
        return atomicTakeOfferModel.getVolume();
    }

    Price getTradePrice() {
        return atomicTakeOfferModel.getTradePrice();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        atomicTakeOfferModel.calculateVolume();
    }

    void applyAmount(Coin amount) {
        atomicTakeOfferModel.applyAmount(amount);
    }

    public Coin getTakerFee() {
        return atomicTakeOfferModel.getTakerFee();
    }

    boolean isMinAmountLessOrEqualAmount() {
        return atomicTakeOfferModel.isMinAmountLessOrEqualAmount();
    }

    boolean isAmountLargerThanOfferAmount() {
        return atomicTakeOfferModel.isAmountLargerThanOfferAmount();
    }

    ReadOnlyObjectProperty<Coin> getAmount() {
        return atomicTakeOfferModel.getAmount();
    }

    public String getCurrencyCode() {
        return atomicTakeOfferModel.getCurrencyCode();
    }

    public String getCurrencyNameAndCode() {
        return atomicTakeOfferModel.getCurrencyNameAndCode();
    }

    public Coin getUsableBsqBalance() {
        return atomicTakeOfferModel.getUsableBsqBalance();
    }

    public boolean isCurrencyForTakerFeeBtc() {
        return atomicTakeOfferModel.isCurrencyForTakerFeeBtc();
    }

    public void setPreferredCurrencyForTakerFeeBtc(boolean isCurrencyForTakerFeeBtc) {
        atomicTakeOfferModel.setPreferredCurrencyForTakerFeeBtc(isCurrencyForTakerFeeBtc);
    }

    public Coin getTakerFeeInBtc() {
        return atomicTakeOfferModel.getTakerFeeInBtc();
    }

    public Coin getTakerFeeInBsq() {
        return atomicTakeOfferModel.getTakerFeeInBsq();
    }

    boolean isTakerFeeValid() {
        return atomicTakeOfferModel.isTakerFeeValid();
    }

    public boolean hasEnoughBtc() {
        return atomicTakeOfferModel.hasEnoughBtc();
    }

    public boolean hasEnoughBsq() {
        return atomicTakeOfferModel.hasEnoughBsq();
    }
}
