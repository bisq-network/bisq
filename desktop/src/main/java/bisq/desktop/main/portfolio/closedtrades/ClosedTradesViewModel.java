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

package bisq.desktop.main.portfolio.closedtrades;

import bisq.desktop.common.model.ActivatableWithDataModel;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.util.DisplayUtils;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.ObservableList;

import java.util.Map;
import java.util.stream.Collectors;

public class ClosedTradesViewModel extends ActivatableWithDataModel<ClosedTradesDataModel> implements ViewModel {
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public ClosedTradesViewModel(ClosedTradesDataModel dataModel,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 BsqWalletService bsqWalletService,
                                 BsqFormatter bsqFormatter,
                                 @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        super(dataModel);
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
    }

    public ObservableList<ClosedTradableListItem> getList() {
        return dataModel.getList();
    }

    String getTradeId(ClosedTradableListItem item) {
        return item.getTradable().getShortId();
    }

    String getAmount(ClosedTradableListItem item) {
        if (item != null && item.getTradable() instanceof Trade)
            return btcFormatter.formatCoin(((Trade) item.getTradable()).getTradeAmount());
        else
            return "";
    }

    String getPrice(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (tradable instanceof Trade)
            return FormattingUtils.formatPrice(((Trade) tradable).getTradePrice());
        else
            return FormattingUtils.formatPrice(tradable.getOffer().getPrice());
    }

    String getPriceDeviation(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (tradable.getOffer().isUseMarketBasedPrice()) {
            return FormattingUtils.formatPercentagePrice(tradable.getOffer().getMarketPriceMargin());
        } else {
            return Res.get("shared.na");
        }
    }

    String getVolume(ClosedTradableListItem item, boolean appendCode) {
        if (item == null) {
            return "";
        }

        if (item.getTradable() instanceof OpenOffer) {
            return "";
        }

        Trade trade = (Trade) item.getTradable();
        return DisplayUtils.formatVolume(trade.getTradeVolume(), appendCode);
    }

    String getVolumeCurrency(ClosedTradableListItem item) {
        if (item == null) {
            return "";
        }
        Volume volume;
        if (item.getTradable() instanceof OpenOffer) {
            OpenOffer openOffer = (OpenOffer) item.getTradable();
            volume = openOffer.getOffer().getVolume();
        } else {
            Trade trade = (Trade) item.getTradable();
            volume = trade.getTradeVolume();
        }
        return volume != null ? volume.getCurrencyCode() : "";
    }

    String getTxFee(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (!wasMyOffer(tradable) && (tradable instanceof Trade)) {
            // taker pays for 3 transactions
            return btcFormatter.formatCoin(((Trade) tradable).getTxFee().multiply(3));
        } else {
            return btcFormatter.formatCoin(tradable.getOffer().getTxFee());
        }
    }

    boolean isCurrencyForTradeFeeBtc(ClosedTradableListItem item) {
        if (item == null) {
            return false;
        }

        Tradable tradable = item.getTradable();
        Offer offer = tradable.getOffer();
        if (wasMyOffer(tradable) || tradable instanceof OpenOffer) {
            // I was maker so we use offer
            return offer.isCurrencyForMakerFeeBtc();
        } else {
            Trade trade = (Trade) tradable;
            String takerFeeTxId = trade.getTakerFeeTxId();
            // If we find our tx in the bsq wallet its a BSQ trade fee tx
            return bsqWalletService.getTransaction(takerFeeTxId) == null;
        }
    }

    String getTradeFee(ClosedTradableListItem item, boolean appendCode) {
        if (item == null) {
            return "";
        }

        Tradable tradable = item.getTradable();
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

    String getBuyerSecurityDeposit(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (tradable.getOffer() != null)
            return btcFormatter.formatCoin(tradable.getOffer().getBuyerSecurityDeposit());
        else
            return "";
    }

    String getSellerSecurityDeposit(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (tradable.getOffer() != null)
            return btcFormatter.formatCoin(tradable.getOffer().getSellerSecurityDeposit());
        else
            return "";
    }

    String getDirectionLabel(ClosedTradableListItem item) {
        return (item != null) ? DisplayUtils.getDirectionWithCode(dataModel.getDirection(item.getTradable().getOffer()), item.getTradable().getOffer().getCurrencyCode()) : "";
    }

    String getDate(ClosedTradableListItem item) {
        return DisplayUtils.formatDateTime(item.getTradable().getDate());
    }

    String getMarketLabel(ClosedTradableListItem item) {
        if ((item == null))
            return "";

        return CurrencyUtil.getCurrencyPair(item.getTradable().getOffer().getCurrencyCode());
    }

    String getState(ClosedTradableListItem item) {
        if (item != null) {
            if (item.getTradable() instanceof Trade) {
                Trade trade = (Trade) item.getTradable();

                if (trade.isWithdrawn() || trade.isPayoutPublished()) {
                    return Res.get("portfolio.closed.completed");
                } else if (trade.getDisputeState() == Trade.DisputeState.DISPUTE_CLOSED) {
                    return Res.get("portfolio.closed.ticketClosed");
                } else if (trade.getDisputeState() == Trade.DisputeState.MEDIATION_CLOSED) {
                    return Res.get("portfolio.closed.mediationTicketClosed");
                } else if (trade.getDisputeState() == Trade.DisputeState.REFUND_REQUEST_CLOSED) {
                    return Res.get("portfolio.closed.ticketClosed");
                } else {
                    log.error("That must not happen. We got a pending state but we are in the closed trades list. state={}", trade.getState().toString());
                    return Res.get("shared.na");
                }
            } else if (item.getTradable() instanceof OpenOffer) {
                OpenOffer.State state = ((OpenOffer) item.getTradable()).getState();
                log.trace("OpenOffer state {}", state);
                switch (state) {
                    case AVAILABLE:
                    case RESERVED:
                    case CLOSED:
                        log.error("Invalid state {}", state);
                        return state.toString();
                    case CANCELED:
                        return Res.get("portfolio.closed.canceled");
                    case DEACTIVATED:
                        log.error("Invalid state {}", state);
                        return state.toString();
                    default:
                        log.error("Unhandled state {}", state);
                        return state.toString();
                }
            }
        }
        return "";
    }

    int getNumPastTrades(Tradable tradable) {
        return dataModel.closedTradableManager.getObservableList().stream()
                .filter(candidate -> {
                    if (!(candidate instanceof Trade) ||
                            !(tradable instanceof Trade)) return false;
                    NodeAddress candidateAddress = ((Trade) candidate).getTradingPeerNodeAddress();
                    NodeAddress tradableAddress = ((Trade) tradable).getTradingPeerNodeAddress();
                    return candidateAddress != null &&
                            tradableAddress != null &&
                            candidateAddress.getFullAddress().equals(tradableAddress.getFullAddress());
                })
                .collect(Collectors.toSet())
                .size();
    }

    boolean wasMyOffer(Tradable tradable) {
        return dataModel.wasMyOffer(tradable);
    }

    public Coin getTotalTradeAmount() {
        return dataModel.getTotalAmount();
    }

    public String getTotalAmountWithVolume(Coin totalTradeAmount) {
        return dataModel.getVolumeInUserFiatCurrency(totalTradeAmount)
                .map(volume -> {
                    return Res.get("closedTradesSummaryWindow.totalAmount.value",
                            btcFormatter.formatCoin(totalTradeAmount, true),
                            DisplayUtils.formatVolumeWithCode(volume));
                })
                .orElse("");
    }

    public Map<String, String> getTotalVolumeByCurrency() {
        return dataModel.getTotalVolumeByCurrency().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> {
                            String currencyCode = entry.getKey();
                            Monetary monetary;
                            if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                                monetary = Altcoin.valueOf(currencyCode, entry.getValue());
                            } else {
                                monetary = Fiat.valueOf(currencyCode, entry.getValue());
                            }
                            return DisplayUtils.formatVolumeWithCode(new Volume(monetary));
                        }
                ));
    }

    public String getTotalTxFee(Coin totalTradeAmount) {
        Coin totalTxFee = dataModel.getTotalTxFee();
        double percentage = ((double) totalTxFee.value) / totalTradeAmount.value;
        return Res.get("closedTradesSummaryWindow.totalMinerFee.value",
                btcFormatter.formatCoin(totalTxFee, true),
                FormattingUtils.formatToPercentWithSymbol(percentage));
    }

    public String getTotalTradeFeeInBtc(Coin totalTradeAmount) {
        Coin totalTradeFee = dataModel.getTotalTradeFee(true);
        double percentage = ((double) totalTradeFee.value) / totalTradeAmount.value;
        return Res.get("closedTradesSummaryWindow.totalTradeFeeInBtc.value",
                btcFormatter.formatCoin(totalTradeFee, true),
                FormattingUtils.formatToPercentWithSymbol(percentage));
    }

    public String getTotalTradeFeeInBsq(Coin totalTradeAmount) {
        return dataModel.getVolume(totalTradeAmount, "USD")
                .filter(v -> v.getValue() > 0)
                .map(tradeAmountVolume -> {
                    Coin totalTradeFee = dataModel.getTotalTradeFee(false);
                    Volume bsqVolumeInUsd = dataModel.getBsqVolumeInUsdWithAveragePrice(totalTradeFee); // with 4 decimal
                    double percentage = ((double) bsqVolumeInUsd.getValue()) / tradeAmountVolume.getValue();
                    return Res.get("closedTradesSummaryWindow.totalTradeFeeInBsq.value",
                            bsqFormatter.formatCoin(totalTradeFee, true),
                            FormattingUtils.formatToPercentWithSymbol(percentage));
                })
                .orElse("");
    }
}
