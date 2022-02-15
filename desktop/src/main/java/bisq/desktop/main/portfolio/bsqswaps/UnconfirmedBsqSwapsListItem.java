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

package bisq.desktop.main.portfolio.bsqswaps;

import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.filtering.FilterableListItem;
import bisq.desktop.util.filtering.FilteringUtils;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionConfidence;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.Tooltip;

import lombok.Getter;

import javax.annotation.Nullable;

class UnconfirmedBsqSwapsListItem implements FilterableListItem {
    @Getter
    private final BsqSwapTrade bsqSwapTrade;
    private final BsqWalletService bsqWalletService;
    private final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    private final BsqSwapTradeManager bsqSwapTradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final String txId;
    @Getter
    private int confirmations = 0;
    @Getter
    private final TxConfidenceIndicator txConfidenceIndicator;
    private final TxConfidenceListener txConfidenceListener;

    UnconfirmedBsqSwapsListItem(
            BsqSwapTrade bsqSwapTrade,
            BsqWalletService bsqWalletService,
            CoinFormatter btcFormatter,
            BsqFormatter bsqFormatter,
            BsqSwapTradeManager bsqSwapTradeManager,
            ClosedTradableManager closedTradableManager) {
        this.bsqSwapTrade = bsqSwapTrade;
        this.bsqWalletService = bsqWalletService;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
        this.bsqSwapTradeManager = bsqSwapTradeManager;
        this.closedTradableManager = closedTradableManager;

        txId = bsqSwapTrade.getTxId();
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");
        Tooltip tooltip = new Tooltip();
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setPrefSize(24, 24);
        txConfidenceIndicator.setTooltip(tooltip);

        txConfidenceListener = new TxConfidenceListener(txId) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence, tooltip);
            }
        };
        bsqWalletService.addTxConfidenceListener(txConfidenceListener);
        updateConfidence(bsqWalletService.getConfidenceForTxId(txId), tooltip);
    }

    private void updateConfidence(@Nullable TransactionConfidence confidence, Tooltip tooltip) {
        if (confidence != null) {
            GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);
            confirmations = confidence.getDepthInBlocks();
        }
    }

    public void cleanup() {
        bsqWalletService.removeTxConfidenceListener(txConfidenceListener);
    }

    public String getTradeId() {
        return bsqSwapTrade.getShortId();
    }

    public Coin getAmount() {
        return bsqSwapTrade.getAmount();
    }

    public String getAmountAsString() {
        return btcFormatter.formatCoin(bsqSwapTrade.getAmount());
    }

    public Price getPrice() {
        return bsqSwapTrade.getPrice();
    }

    public String getPriceAsString() {
        return FormattingUtils.formatPrice(bsqSwapTrade.getPrice());
    }

    public String getVolumeAsString() {
        return VolumeUtil.formatVolumeWithCode(bsqSwapTrade.getVolume());
    }

    public Coin getTxFee() {
        return Coin.valueOf(bsqSwapTrade.getBsqSwapProtocolModel().getTxFee());
    }

    public String getTxFeeAsString() {
        return btcFormatter.formatCoinWithCode(Coin.valueOf(bsqSwapTrade.getBsqSwapProtocolModel().getTxFee()));
    }

    public String getTradeFeeAsString() {
        if (bsqSwapTradeManager.wasMyOffer(bsqSwapTrade.getOffer())) {
            return bsqFormatter.formatCoinWithCode(bsqSwapTrade.getMakerFeeAsLong());
        } else {
            return bsqFormatter.formatCoinWithCode(bsqSwapTrade.getTakerFeeAsLong());
        }
    }

    public String getDirectionLabel() {
        Offer offer = bsqSwapTrade.getOffer();
        OfferDirection direction = bsqSwapTradeManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
        return DisplayUtils.getDirectionWithCode(direction, bsqSwapTrade.getOffer().getCurrencyCode());
    }

    public String getDateAsString() {
        return DisplayUtils.formatDateTime(bsqSwapTrade.getDate());
    }

    public String getMarketLabel() {
        return CurrencyUtil.getCurrencyPair(bsqSwapTrade.getOffer().getCurrencyCode());
    }

    public int getConfidence() {
        return getConfirmations();
    }

    public int getNumPastTrades() {
        return closedTradableManager.getNumPastTrades(bsqSwapTrade);
    }

    @Override
    public boolean match(String filterString) {
        if (filterString.isEmpty()) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getDateAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getMarketLabel(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getPriceAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getVolumeAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getAmountAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getTradeFeeAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getTxFeeAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(String.valueOf(getConfidence()), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getDirectionLabel(), filterString)) {
            return true;
        }
        if (FilteringUtils.match(getBsqSwapTrade(), filterString)) {
            return true;
        }
        return FilteringUtils.match(getBsqSwapTrade().getOffer(), filterString);
    }
}
