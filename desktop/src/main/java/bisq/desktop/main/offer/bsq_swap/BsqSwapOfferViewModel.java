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

package bisq.desktop.main.offer.bsq_swap;

import bisq.desktop.common.model.ActivatableWithDataModel;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static javafx.beans.binding.Bindings.createStringBinding;

@Slf4j
public abstract class BsqSwapOfferViewModel<M extends BsqSwapOfferDataModel> extends ActivatableWithDataModel<M> {
    @Getter
    protected final StringProperty inputAmount = new SimpleStringProperty();
    @Getter
    protected final StringProperty payoutAmount = new SimpleStringProperty();
    protected final StringProperty btcTradeAmount = new SimpleStringProperty();
    protected final StringProperty bsqTradeAmount = new SimpleStringProperty();
    protected final BsqFormatter bsqFormatter;
    @Getter
    protected AccountAgeWitnessService accountAgeWitnessService;
    protected final CoinFormatter btcFormatter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqSwapOfferViewModel(M dataModel,
                                 CoinFormatter btcFormatter,
                                 BsqFormatter bsqFormatter,
                                 AccountAgeWitnessService accountAgeWitnessService) {
        super(dataModel);

        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
        this.accountAgeWitnessService = accountAgeWitnessService;

        createListeners();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void createListeners();

    protected abstract void addListeners();

    protected abstract void removeListeners();

    protected void addBindings() {
        inputAmount.bind(createStringBinding(() -> {
                    CoinFormatter formatter = dataModel.isBuyer() ? bsqFormatter : btcFormatter;
                    return formatter.formatCoinWithCode(dataModel.getInputAmountAsCoin().get());
                },
                dataModel.getInputAmountAsCoin()));
        payoutAmount.bind(createStringBinding(() -> {
                    CoinFormatter formatter = dataModel.isBuyer() ? btcFormatter : bsqFormatter;
                    return formatter.formatCoinWithCode(dataModel.getPayoutAmountAsCoin().get());
                },
                dataModel.getInputAmountAsCoin()));
        btcTradeAmount.bind(createStringBinding(() -> btcFormatter.formatCoinWithCode(dataModel.getBtcAmount().get()),
                dataModel.getBtcAmount()));

        bsqTradeAmount.bind(createStringBinding(() -> bsqFormatter.formatCoinWithCode(dataModel.getBsqAmount().get()),
                dataModel.getBsqAmount()));
    }

    protected void removeBindings() {
        inputAmount.unbind();
        payoutAmount.unbind();
        btcTradeAmount.unbind();
        bsqTradeAmount.unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected String getTradeFee() {
        return bsqFormatter.formatCoinWithCode(dataModel.getTradeFee());
    }

    public String getInputAmountDetails() {
        String details;
        if (dataModel.isBuyer()) {
            details = Res.get("bsqSwapOffer.inputAmount.details.buyer",
                    bsqTradeAmount.get(), getTradeFee());
        } else {
            details = Res.get("bsqSwapOffer.inputAmount.details.seller",
                    btcTradeAmount.get(), getTxFee());
        }

        return details;
    }

    public String getPayoutAmountDetails() {
        String details;
        if (dataModel.isBuyer()) {
            details = Res.get("bsqSwapOffer.outputAmount.details.buyer",
                    btcTradeAmount.get(), getTxFee());
        } else {
            details = Res.get("bsqSwapOffer.outputAmount.details.seller",
                    bsqTradeAmount.get(), getTradeFee());
        }

        return details;
    }

    public String getTxFee() {
        try {
            Coin txFee = dataModel.getTxFee();
            return btcFormatter.formatCoinWithCode(txFee);
        } catch (InsufficientMoneyException e) {
            Coin txFee = dataModel.getEstimatedTxFee();
            return Res.get("bsqSwapOffer.estimated", btcFormatter.formatCoinWithCode(txFee));
        }
    }

    public String getMissingFunds(Coin missing) {
        return dataModel.isBuyer() ?
                bsqFormatter.formatCoinWithCode(missing) :
                btcFormatter.formatCoinWithCode(missing);
    }
}
