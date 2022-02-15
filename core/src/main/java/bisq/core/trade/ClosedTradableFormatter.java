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

package bisq.core.trade;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Volume;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.ClosedTradableUtil.*;
import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.DISPUTE_CLOSED;
import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.MEDIATION_CLOSED;
import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.REFUND_REQUEST_CLOSED;
import static bisq.core.util.FormattingUtils.BTC_FORMATTER_KEY;
import static bisq.core.util.FormattingUtils.formatPercentagePrice;
import static bisq.core.util.FormattingUtils.formatToPercentWithSymbol;
import static bisq.core.util.VolumeUtil.formatVolume;
import static bisq.core.util.VolumeUtil.formatVolumeWithCode;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING;

@Slf4j
@Singleton
public class ClosedTradableFormatter {
    // Resource bundle i18n keys with Desktop UI specific property names,
    // having "generic-enough" property values to be referenced in the core layer.
    private static final String I18N_KEY_TOTAL_AMOUNT = "closedTradesSummaryWindow.totalAmount.value";
    private static final String I18N_KEY_TOTAL_TX_FEE = "closedTradesSummaryWindow.totalMinerFee.value";
    private static final String I18N_KEY_TOTAL_TRADE_FEE_BTC = "closedTradesSummaryWindow.totalTradeFeeInBtc.value";
    private static final String I18N_KEY_TOTAL_TRADE_FEE_BSQ = "closedTradesSummaryWindow.totalTradeFeeInBsq.value";

    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    private final BsqWalletService bsqWalletService;
    private final ClosedTradableManager closedTradableManager;

    @Inject
    public ClosedTradableFormatter(ClosedTradableManager closedTradableManager,
                                   BsqFormatter bsqFormatter,
                                   @Named(BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                   BsqWalletService bsqWalletService) {
        this.closedTradableManager = closedTradableManager;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.bsqWalletService = bsqWalletService;
    }

    public String getAmountAsString(Tradable tradable) {
        return tradable.getOptionalAmount().map(btcFormatter::formatCoin).orElse("");
    }

    public String getTotalAmountWithVolumeAsString(Coin totalTradeAmount, Volume volume) {
        return Res.get(I18N_KEY_TOTAL_AMOUNT,
                btcFormatter.formatCoin(totalTradeAmount, true),
                formatVolumeWithCode(volume));
    }

    public String getTxFeeAsString(Tradable tradable) {
        return btcFormatter.formatCoin(getTxFee(tradable));
    }

    public String getTotalTxFeeAsString(Coin totalTradeAmount, Coin totalTxFee) {
        double percentage = ((double) totalTxFee.value) / totalTradeAmount.value;
        return Res.get(I18N_KEY_TOTAL_TX_FEE,
                btcFormatter.formatCoin(totalTxFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public String getBuyerSecurityDepositAsString(Tradable tradable) {
        return isBsqSwapTrade(tradable) ? Res.get("shared.na") :
                btcFormatter.formatCoin(tradable.getOffer().getBuyerSecurityDeposit());
    }

    public String getSellerSecurityDepositAsString(Tradable tradable) {
        return isBsqSwapTrade(tradable) ? Res.get("shared.na") :
                btcFormatter.formatCoin(tradable.getOffer().getSellerSecurityDeposit());
    }

    public String getTotalTradeFeeInBsqAsString(Coin totalTradeFee,
                                                Volume tradeAmountVolume,
                                                Volume bsqVolumeInUsd) {
        double percentage = ((double) bsqVolumeInUsd.getValue()) / tradeAmountVolume.getValue();
        return Res.get(I18N_KEY_TOTAL_TRADE_FEE_BSQ,
                bsqFormatter.formatCoin(totalTradeFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public String getTradeFeeAsString(Tradable tradable, boolean appendCode) {
        if (closedTradableManager.isBsqTradeFee(tradable)) {
            return bsqFormatter.formatCoin(Coin.valueOf(closedTradableManager.getBsqTradeFee(tradable)), appendCode);
        } else {
            closedTradableManager.getBtcTradeFee(tradable);
            return btcFormatter.formatCoin(Coin.valueOf(closedTradableManager.getBtcTradeFee(tradable)), appendCode);
        }
    }

    public String getTotalTradeFeeInBtcAsString(Coin totalTradeAmount, Coin totalTradeFee) {
        double percentage = ((double) totalTradeFee.value) / totalTradeAmount.value;
        return Res.get(I18N_KEY_TOTAL_TRADE_FEE_BTC,
                btcFormatter.formatCoin(totalTradeFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public String getPriceDeviationAsString(Tradable tradable) {
        if (tradable.getOffer().isUseMarketBasedPrice()) {
            return formatPercentagePrice(tradable.getOffer().getMarketPriceMargin());
        } else {
            return Res.get("shared.na");
        }
    }

    public String getVolumeAsString(Tradable tradable, boolean appendCode) {
        return tradable.getOptionalVolume().map(volume -> formatVolume(volume, appendCode)).orElse("");
    }

    public String getVolumeCurrencyAsString(Tradable tradable) {
        return tradable.getOptionalVolume().map(Volume::getCurrencyCode).orElse("");
    }

    public String getPriceAsString(Tradable tradable) {
        return tradable.getOptionalPrice().map(FormattingUtils::formatPrice).orElse("");
    }

    public Map<String, String> getTotalVolumeByCurrencyAsString(List<Tradable> tradableList) {
        return getTotalVolumeByCurrency(tradableList).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> {
                            String currencyCode = entry.getKey();
                            Monetary monetary;
                            if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                                monetary = Altcoin.valueOf(currencyCode, entry.getValue());
                            } else {
                                monetary = Fiat.valueOf(currencyCode, entry.getValue());
                            }
                            return formatVolumeWithCode(new Volume(monetary));
                        }
                ));
    }

    public String getStateAsString(Tradable tradable) {
        if (tradable == null) {
            return "";
        }

        if (isBisqV1Trade(tradable)) {
            Trade trade = castToTrade(tradable);
            if (trade.isWithdrawn() || trade.isPayoutPublished()) {
                return Res.get("portfolio.closed.completed");
            } else if (trade.getDisputeState() == DISPUTE_CLOSED) {
                return Res.get("portfolio.closed.ticketClosed");
            } else if (trade.getDisputeState() == MEDIATION_CLOSED) {
                return Res.get("portfolio.closed.mediationTicketClosed");
            } else if (trade.getDisputeState() == REFUND_REQUEST_CLOSED) {
                return Res.get("portfolio.closed.ticketClosed");
            } else {
                log.error("That must not happen. We got a pending state but we are in"
                                + " the closed trades list. state={}",
                        trade.getTradeState().name());
                return Res.get("shared.na");
            }
        } else if (isOpenOffer(tradable)) {
            OpenOffer.State state = ((OpenOffer) tradable).getState();
            log.trace("OpenOffer state={}", state);
            switch (state) {
                case AVAILABLE:
                case RESERVED:
                case CLOSED:
                case DEACTIVATED:
                    log.error("Invalid state {}", state);
                    return state.name();
                case CANCELED:
                    return Res.get("portfolio.closed.canceled");
                default:
                    log.error("Unhandled state {}", state);
                    return state.name();
            }
        } else if (isBsqSwapTrade(tradable)) {
            String txId = castToBsqSwapTrade(tradable).getTxId();
            TransactionConfidence confidence = bsqWalletService.getConfidenceForTxId(txId);
            if (confidence != null && confidence.getConfidenceType() == BUILDING) {
                return Res.get("confidence.confirmed.short");
            } else if (confidence != null && confidence.getConfidenceType() == PENDING) {
                return Res.get("confidence.pending");
            } else {
                log.warn("Unexpected confidence in a BSQ swap trade which has been moved to closed trades. " +
                                "This could happen at a wallet SPV resync or a reorg. confidence={} tradeID={}",
                        confidence, tradable.getId());
            }
        }
        return Res.get("shared.na");
    }
}
