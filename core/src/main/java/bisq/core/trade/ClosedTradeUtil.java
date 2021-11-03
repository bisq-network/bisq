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
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.model.MakerTrade;
import bisq.core.trade.model.TakerTrade;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.DISPUTE_CLOSED;
import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.MEDIATION_CLOSED;
import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.REFUND_REQUEST_CLOSED;
import static bisq.core.util.AveragePriceUtil.getAveragePriceTuple;
import static bisq.core.util.FormattingUtils.BTC_FORMATTER_KEY;
import static bisq.core.util.FormattingUtils.formatPercentagePrice;
import static bisq.core.util.FormattingUtils.formatToPercentWithSymbol;
import static bisq.core.util.VolumeUtil.formatVolume;
import static bisq.core.util.VolumeUtil.formatVolumeWithCode;

@Slf4j
public class ClosedTradeUtil {

    // Resource bundle i18n keys with Desktop UI specific property names,
    // having "generic-enough" property values to be referenced in the core layer.
    private static final String I18N_KEY_TOTAL_AMOUNT = "closedTradesSummaryWindow.totalAmount.value";
    private static final String I18N_KEY_TOTAL_TX_FEE = "closedTradesSummaryWindow.totalMinerFee.value";
    private static final String I18N_KEY_TOTAL_TRADE_FEE_BTC = "closedTradesSummaryWindow.totalTradeFeeInBtc.value";
    private static final String I18N_KEY_TOTAL_TRADE_FEE_BSQ = "closedTradesSummaryWindow.totalTradeFeeInBsq.value";

    private final ClosedTradableManager closedTradableManager;
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    private final Preferences preferences;
    private final KeyRing keyRing;
    private final TradeStatisticsManager tradeStatisticsManager;

    @Inject
    public ClosedTradeUtil(ClosedTradableManager closedTradableManager,
                           BsqWalletService bsqWalletService,
                           BsqFormatter bsqFormatter,
                           @Named(BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                           Preferences preferences,
                           KeyRing keyRing,
                           TradeStatisticsManager tradeStatisticsManager) {
        this.closedTradableManager = closedTradableManager;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.preferences = preferences;
        this.keyRing = keyRing;
        this.tradeStatisticsManager = tradeStatisticsManager;
    }

    public Coin getTotalAmount(List<Tradable> tradableList) {
        return Coin.valueOf(tradableList.stream()
                .flatMap(tradable -> tradable.getOptionalAmountAsLong().stream())
                .mapToLong(value -> value)
                .sum());
    }

    public String getAmountAsString(Tradable tradable) {
        return tradable.getOptionalAmount().map(btcFormatter::formatCoin).orElse("");
    }

    public String getTotalAmountWithVolumeAsString(Coin totalTradeAmount, Volume volume) {
        return Res.get(I18N_KEY_TOTAL_AMOUNT,
                btcFormatter.formatCoin(totalTradeAmount, true),
                formatVolumeWithCode(volume));
    }

    public String getPriceAsString(Tradable tradable) {
        return tradable.getOptionalPrice().map(FormattingUtils::formatPrice).orElse("");
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

    public Map<String, Long> getTotalVolumeByCurrency(List<Tradable> tradableList) {
        Map<String, Long> map = new HashMap<>();
        tradableList.stream()
                .flatMap(tradable -> tradable.getOptionalVolume().stream())
                .forEach(volume -> {
                    String currencyCode = volume.getCurrencyCode();
                    map.putIfAbsent(currencyCode, 0L);
                    map.put(currencyCode, volume.getValue() + map.get(currencyCode));
                });
        return map;
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

    public Volume getBsqVolumeInUsdWithAveragePrice(Coin amount) {
        Tuple2<Price, Price> tuple = getAveragePriceTuple(preferences, tradeStatisticsManager, 30);
        Price usdPrice = tuple.first;
        long value = Math.round(amount.value * usdPrice.getValue() / 100d);
        return new Volume(Fiat.valueOf("USD", value));
    }

    public Coin getTotalTxFee(List<Tradable> tradableList) {
        return Coin.valueOf(tradableList.stream()
                .mapToLong(tradable -> getTxFee(tradable).getValue())
                .sum());
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

    public boolean isCurrencyForTradeFeeBtc(Tradable tradable) {
        return !isBsqTradeFee(tradable);
    }

    public Coin getTotalTradeFee(List<Tradable> tradableList, boolean expectBtcFee) {
        return Coin.valueOf(tradableList.stream()
                .mapToLong(tradable -> getTradeFee(tradable, expectBtcFee))
                .sum());
    }

    public String getTradeFeeAsString(Tradable tradable, boolean appendCode) {
        if (isBsqTradeFee(tradable)) {
            return bsqFormatter.formatCoin(Coin.valueOf(getBsqTradeFee(tradable)), appendCode);
        } else {
            getBtcTradeFee(tradable);
            return btcFormatter.formatCoin(Coin.valueOf(getBtcTradeFee(tradable)), appendCode);
        }
    }

    public String getTotalTradeFeeInBtcAsString(Coin totalTradeAmount, Coin totalTradeFee) {
        double percentage = ((double) totalTradeFee.value) / totalTradeAmount.value;
        return Res.get(I18N_KEY_TOTAL_TRADE_FEE_BTC,
                btcFormatter.formatCoin(totalTradeFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public String getBuyerSecurityDepositAsString(Tradable tradable) {
        return isBsqSwapTrade(tradable) ? "" :
                btcFormatter.formatCoin(tradable.getOffer().getBuyerSecurityDeposit());
    }

    public String getSellerSecurityDepositAsString(Tradable tradable) {
        return isBsqSwapTrade(tradable) ? "" :
                btcFormatter.formatCoin(tradable.getOffer().getSellerSecurityDeposit());
    }

    public String getMarketLabel(Tradable tradable) {
        return CurrencyUtil.getCurrencyPair(tradable.getOffer().getCurrencyCode());
    }

    public int getNumPastTrades(Tradable tradable) {
        if (isOpenOffer(tradable)) {
            return 0;
        }
        NodeAddress addressInTrade = castToTradeModel(tradable).getTradingPeerNodeAddress();
        return getClosedTradableStream()
                .filter(this::isTradeModel)
                .map(this::castToTradeModel)
                .map(TradeModel::getTradingPeerNodeAddress)
                .filter(Objects::nonNull)
                .filter(address -> address.equals(addressInTrade))
                .collect(Collectors.toSet())
                .size();
    }

    public String getTotalTradeFeeInBsqAsString(Coin totalTradeFee,
                                                Volume tradeAmountVolume,
                                                Volume bsqVolumeInUsd) {
        double percentage = ((double) bsqVolumeInUsd.getValue()) / tradeAmountVolume.getValue();
        return Res.get(I18N_KEY_TOTAL_TRADE_FEE_BSQ,
                bsqFormatter.formatCoin(totalTradeFee, true),
                formatToPercentWithSymbol(percentage));
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
            BsqSwapTrade bsqSwapTrade = castToBsqSwapTrade(tradable);
            //todo
        }
        return "";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Stream<Trade> getClosedTradableStream() {
        return closedTradableManager.getClosedTrades().stream();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fee utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private long getTradeFee(Tradable tradable, boolean expectBtcFee) {
        return expectBtcFee ? getBtcTradeFee(tradable) : getBsqTradeFee(tradable);
    }

    private long getBtcTradeFee(Tradable tradable) {
        if (isBsqSwapTrade(tradable) || isBsqTradeFee(tradable)) {
            return 0L;
        }
        return isMaker(tradable) ?
                tradable.getOptionalMakerFee().orElse(Coin.ZERO).value :
                tradable.getOptionalTakerFee().orElse(Coin.ZERO).value;
    }

    private long getBsqTradeFee(Tradable tradable) {
        if (isBsqSwapTrade(tradable) || isBsqTradeFee(tradable)) {
            return isMaker(tradable) ?
                    tradable.getOptionalMakerFee().orElse(Coin.ZERO).value :
                    tradable.getOptionalTakerFee().orElse(Coin.ZERO).value;
        }
        return 0L;
    }

    private boolean isBsqTradeFee(Tradable tradable) {
        if (isBsqSwapTrade(tradable)) {
            return true;
        }

        if (isMaker(tradable)) {
            return !tradable.getOffer().isCurrencyForMakerFeeBtc();
        }

        String feeTxId = castToTrade(tradable).getTakerFeeTxId();
        return bsqWalletService.getTransaction(feeTxId) != null;
    }

    private Coin getTxFee(Tradable tradable) {
        Coin txFee = tradable.getOptionalTxFee().orElse(Coin.ZERO);
        if (isBisqV1TakerTrade(tradable)) {
            txFee = txFee.multiply(3);
        }
        return txFee;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isOpenOffer(Tradable tradable) {
        return tradable instanceof OpenOffer;
    }

    private boolean isTradeModel(Tradable tradable) {
        return tradable instanceof TradeModel;
    }

    private boolean isMaker(Tradable tradable) {
        return tradable instanceof MakerTrade || tradable.getOffer().isMyOffer(keyRing);
    }

    private boolean isTakerTrade(Tradable tradable) {
        return tradable instanceof TakerTrade;
    }

    private boolean isBsqSwapTrade(Tradable tradable) {
        return tradable instanceof BsqSwapTrade;
    }

    private boolean isBisqV1Trade(Tradable tradable) {
        return tradable instanceof Trade;
    }

    private boolean isBisqV1TakerTrade(Tradable tradable) {
        return isBisqV1Trade(tradable) && isTakerTrade(tradable);
    }

    private Trade castToTrade(Tradable tradable) {
        return (Trade) tradable;
    }

    private TradeModel castToTradeModel(Tradable tradable) {
        return (TradeModel) tradable;
    }

    private BsqSwapTrade castToBsqSwapTrade(Tradable tradable) {
        return (BsqSwapTrade) tradable;
    }
}
