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
import bisq.core.locale.Res;
import bisq.core.monetary.Volume;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionConfidence;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.DISPUTE_CLOSED;
import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.MEDIATION_CLOSED;
import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.REFUND_REQUEST_CLOSED;
import static bisq.core.util.FormattingUtils.BTC_FORMATTER_KEY;
import static bisq.core.util.FormattingUtils.formatToPercentWithSymbol;
import static bisq.core.util.VolumeUtil.formatVolumeWithCode;

@Slf4j
@Singleton
public class ClosedTradeFormatter {
    // Resource bundle i18n keys with Desktop UI specific property names,
    // having "generic-enough" property values to be referenced in the core layer.
    private static final String I18N_KEY_TOTAL_AMOUNT = "closedTradesSummaryWindow.totalAmount.value";
    private static final String I18N_KEY_TOTAL_TX_FEE = "closedTradesSummaryWindow.totalMinerFee.value";
    private static final String I18N_KEY_TOTAL_TRADE_FEE_BTC = "closedTradesSummaryWindow.totalTradeFeeInBtc.value";
    private static final String I18N_KEY_TOTAL_TRADE_FEE_BSQ = "closedTradesSummaryWindow.totalTradeFeeInBsq.value";

    private final ClosedTradeUtil closedTradeUtil;
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    private final BsqWalletService bsqWalletService;

    @Inject
    public ClosedTradeFormatter(ClosedTradeUtil closedTradeUtil,
                                BsqFormatter bsqFormatter,
                                @Named(BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                BsqWalletService bsqWalletService) {
        this.closedTradeUtil = closedTradeUtil;
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
        return btcFormatter.formatCoin(closedTradeUtil.getTxFee(tradable));
    }

    public String getTotalTxFeeAsString(Coin totalTradeAmount, Coin totalTxFee) {
        double percentage = ((double) totalTxFee.value) / totalTradeAmount.value;
        return Res.get(I18N_KEY_TOTAL_TX_FEE,
                btcFormatter.formatCoin(totalTxFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public String getBuyerSecurityDepositAsString(Tradable tradable) {
        return closedTradeUtil.isBsqSwapTrade(tradable) ? Res.get("shared.na") :
                btcFormatter.formatCoin(tradable.getOffer().getBuyerSecurityDeposit());
    }

    public String getSellerSecurityDepositAsString(Tradable tradable) {
        return closedTradeUtil.isBsqSwapTrade(tradable) ? Res.get("shared.na") :
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
        if (closedTradeUtil.isBsqTradeFee(tradable)) {
            return bsqFormatter.formatCoin(Coin.valueOf(closedTradeUtil.getBsqTradeFee(tradable)), appendCode);
        } else {
            closedTradeUtil.getBtcTradeFee(tradable);
            return btcFormatter.formatCoin(Coin.valueOf(closedTradeUtil.getBtcTradeFee(tradable)), appendCode);
        }
    }

    public String getTotalTradeFeeInBtcAsString(Coin totalTradeAmount, Coin totalTradeFee) {
        double percentage = ((double) totalTradeFee.value) / totalTradeAmount.value;
        return Res.get(I18N_KEY_TOTAL_TRADE_FEE_BTC,
                btcFormatter.formatCoin(totalTradeFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public String getStateAsString(Tradable tradable) {
        if (tradable == null) {
            return "";
        }

        if (closedTradeUtil.isBisqV1Trade(tradable)) {
            Trade trade = closedTradeUtil.castToTrade(tradable);
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
        } else if (closedTradeUtil.isOpenOffer(tradable)) {
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
        } else if (closedTradeUtil.isBsqSwapTrade(tradable)) {
            String txId = closedTradeUtil.castToBsqSwapTrade(tradable).getTxId();
            TransactionConfidence confidence = bsqWalletService.getConfidenceForTxId(txId);
            if (confidence != null && confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                return Res.get("confidence.confirmed.short");
            } else {
                log.warn("Unexpected confidence in a BSQ swap trade which has been moved to closed trades. " +
                                "This could happen at a wallet SPV resycn or a reorg. confidence={} tradeID={}",
                        confidence, tradable.getId());
            }
        }
        return Res.get("shared.na");
    }
}
