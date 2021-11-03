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

package bisq.core.trade.bisq_v1;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

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

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.DISPUTE_CLOSED;
import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.MEDIATION_CLOSED;
import static bisq.core.trade.model.bisq_v1.Trade.DisputeState.REFUND_REQUEST_CLOSED;
import static bisq.core.util.AveragePriceUtil.getAveragePriceTuple;
import static bisq.core.util.FormattingUtils.BTC_FORMATTER_KEY;
import static bisq.core.util.FormattingUtils.formatPercentagePrice;
import static bisq.core.util.FormattingUtils.formatPrice;
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
    private final TradeStatisticsManager tradeStatisticsManager;

    @Inject
    public ClosedTradeUtil(ClosedTradableManager closedTradableManager,
                           BsqWalletService bsqWalletService,
                           BsqFormatter bsqFormatter,
                           @Named(BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                           Preferences preferences,
                           TradeStatisticsManager tradeStatisticsManager) {
        this.closedTradableManager = closedTradableManager;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.preferences = preferences;
        this.tradeStatisticsManager = tradeStatisticsManager;
    }

    public boolean wasMyOffer(Tradable tradable) {
        return closedTradableManager.wasMyOffer(tradable.getOffer());
    }

    public Coin getTotalAmount(List<Tradable> tradableList) {
        return Coin.valueOf(tradableList.stream()
                .filter(e -> e instanceof Trade)
                .map(e -> (Trade) e)
                .mapToLong(Trade::getTradeAmountAsLong)
                .sum());
    }

    public String getAmountAsString(Tradable tradable) {
        if (tradable instanceof Trade)
            return btcFormatter.formatCoin(((Trade) tradable).getAmount());
        else
            return "";
    }

    public String getTotalAmountWithVolumeAsString(Coin totalTradeAmount, Volume volume) {
        return Res.get(I18N_KEY_TOTAL_AMOUNT,
                btcFormatter.formatCoin(totalTradeAmount, true),
                formatVolumeWithCode(volume));
    }

    public String getPriceAsString(Tradable tradable) {
        if (tradable instanceof Trade)
            return formatPrice(((Trade) tradable).getPrice());
        else
            return formatPrice(tradable.getOffer().getPrice());
    }

    public String getPriceDeviationAsString(Tradable tradable) {
        if (tradable.getOffer().isUseMarketBasedPrice()) {
            return formatPercentagePrice(tradable.getOffer().getMarketPriceMargin());
        } else {
            return Res.get("shared.na");
        }
    }

    public String getVolumeAsString(Tradable tradable, boolean appendCode) {
        if (tradable instanceof OpenOffer) {
            return "";
        }

        Trade trade = (Trade) tradable;
        return formatVolume(trade.getTradeVolume(), appendCode);
    }

    public String getVolumeCurrencyAsString(Tradable tradable) {
        Volume volume;
        if (tradable instanceof OpenOffer) {
            OpenOffer openOffer = (OpenOffer) tradable;
            volume = openOffer.getOffer().getVolume();
        } else {
            Trade trade = (Trade) tradable;
            volume = trade.getTradeVolume();
        }
        return volume != null ? volume.getCurrencyCode() : "";
    }

    public Map<String, Long> getTotalVolumeByCurrency(List<Tradable> tradableList) {
        Map<String, Long> map = new HashMap<>();
        tradableList.stream()
                .filter(e -> e instanceof Trade)
                .map(e -> (Trade) e)
                .map(Trade::getTradeVolume)
                .filter(Objects::nonNull)
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
                .mapToLong(tradable -> {
                    if (wasMyOffer(tradable) || tradable instanceof OpenOffer) {
                        return tradable.getOffer().getTxFee().value;
                    } else {
                        // taker pays for 3 transactions
                        return ((Trade) tradable).getTxFee().multiply(3).value;
                    }
                })
                .sum());
    }

    public String getTxFeeAsString(Tradable tradable) {
        if (!wasMyOffer(tradable) && (tradable instanceof Trade)) {
            // taker pays for 3 transactions
            return btcFormatter.formatCoin(((Trade) tradable).getTxFee().multiply(3));
        } else {
            return btcFormatter.formatCoin(tradable.getOffer().getTxFee());
        }
    }

    public String getTotalTxFeeAsString(Coin totalTradeAmount, Coin totalTxFee) {
        double percentage = ((double) totalTxFee.value) / totalTradeAmount.value;
        return Res.get(I18N_KEY_TOTAL_TX_FEE,
                btcFormatter.formatCoin(totalTxFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public boolean isCurrencyForTradeFeeBtc(Tradable tradable) {
        Offer offer = tradable.getOffer();
        if (wasMyOffer(tradable) || tradable instanceof OpenOffer) {
            // I was maker so we use offer
            return offer.isCurrencyForMakerFeeBtc();
        } else {
            Trade trade = (Trade) tradable;
            String takerFeeTxId = trade.getTakerFeeTxId();
            // If we find our tx in the bsq wallet it's a BSQ trade fee tx.
            return bsqWalletService.getTransaction(takerFeeTxId) == null;
        }
    }

    public Coin getTotalTradeFee(List<Tradable> tradableList, boolean expectBtcFee) {
        return Coin.valueOf(tradableList.stream()
                .mapToLong(tradable -> getTradeFee(tradable, expectBtcFee))
                .sum());
    }

    public String getTradeFeeAsString(Tradable tradable, boolean appendCode) {
        Offer offer = tradable.getOffer();
        if (wasMyOffer(tradable) || tradable instanceof OpenOffer) {
            CoinFormatter formatter = offer.isCurrencyForMakerFeeBtc() ? btcFormatter : bsqFormatter;
            return formatter.formatCoin(offer.getMakerFee(), appendCode);
        } else {
            Trade trade = (Trade) tradable;
            String takerFeeTxId = trade.getTakerFeeTxId();
            if (bsqWalletService.getTransaction(takerFeeTxId) == null) {
                // Was BTC fee
                return btcFormatter.formatCoin(trade.getTakerFee(), appendCode);
            } else {
                // BSQ fee
                return bsqFormatter.formatCoin(trade.getTakerFee(), appendCode);
            }
        }
    }

    public String getTotalTradeFeeInBtcAsString(Coin totalTradeAmount, Coin totalTradeFee) {
        double percentage = ((double) totalTradeFee.value) / totalTradeAmount.value;
        return Res.get(I18N_KEY_TOTAL_TRADE_FEE_BTC,
                btcFormatter.formatCoin(totalTradeFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public String getBuyerSecurityDepositAsString(Tradable tradable) {
        if (tradable.getOffer() != null)
            return btcFormatter.formatCoin(tradable.getOffer().getBuyerSecurityDeposit());
        else
            return "";
    }

    public String getSellerSecurityDepositAsString(Tradable tradable) {
        if (tradable.getOffer() != null)
            return btcFormatter.formatCoin(tradable.getOffer().getSellerSecurityDeposit());
        else
            return "";
    }

    public String getMarketLabel(Tradable tradable) {
        return CurrencyUtil.getCurrencyPair(tradable.getOffer().getCurrencyCode());
    }

    public int getNumPastTrades(Tradable tradable) {
        if (!(tradable instanceof Trade))
            return 0;

        return closedTradableManager.getClosedTrades().stream()
                .filter(candidate -> {
                    NodeAddress candidateAddress = candidate.getTradingPeerNodeAddress();
                    NodeAddress tradableAddress = ((Trade) tradable).getTradingPeerNodeAddress();
                    return candidateAddress != null
                            && tradableAddress != null
                            && candidateAddress.getFullAddress().equals(tradableAddress.getFullAddress());
                })
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
        if (tradable != null) {
            if (tradable instanceof Trade) {
                Trade trade = (Trade) tradable;

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
            } else if (tradable instanceof OpenOffer) {
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
            }
        }
        return "";
    }

    protected long getTradeFee(Tradable tradable, boolean expectBtcFee) {
        Offer offer = tradable.getOffer();
        if (wasMyOffer(tradable) || tradable instanceof OpenOffer) {
            String makerFeeTxId = offer.getOfferFeePaymentTxId();
            boolean notInBsqWallet = bsqWalletService.getTransaction(makerFeeTxId) == null;
            if (expectBtcFee) {
                if (notInBsqWallet) {
                    return offer.getMakerFee().value;
                } else {
                    return 0;
                }
            } else {
                if (notInBsqWallet) {
                    return 0;
                } else {
                    return offer.getMakerFee().value;
                }
            }
        } else {
            Trade trade = (Trade) tradable;
            String takerFeeTxId = trade.getTakerFeeTxId();
            boolean notInBsqWallet = bsqWalletService.getTransaction(takerFeeTxId) == null;
            if (expectBtcFee) {
                if (notInBsqWallet) {
                    return trade.getTakerFee().value;
                } else {
                    return 0;
                }
            } else {
                if (notInBsqWallet) {
                    return 0;
                } else {
                    return trade.getTakerFee().value;
                }
            }
        }
    }
}
