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
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class ClosedTradesViewModel extends ActivatableWithDataModel<ClosedTradesDataModel> implements ViewModel {
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    final AccountAgeWitnessService accountAgeWitnessService;

    @Inject
    public ClosedTradesViewModel(ClosedTradesDataModel dataModel,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 BtcWalletService btcWalletService,
                                 BsqWalletService bsqWalletService,
                                 BsqFormatter bsqFormatter,
                                 @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        super(dataModel);
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.btcWalletService = btcWalletService;
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
        else if (item != null && item.getTradable() instanceof OpenOffer)
            return "-";
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

    String getVolume(ClosedTradableListItem item) {
        if (item != null && item.getTradable() instanceof Trade)
            return DisplayUtils.formatVolumeWithCode(((Trade) item.getTradable()).getTradeVolume());
        else if (item != null && item.getTradable() instanceof OpenOffer)
            return "-";
        else
            return "";
    }

    String getTxFee(ClosedTradableListItem item) {
        if (item == null)
            return "";
        Tradable tradable = item.getTradable();
        if (!wasMyOffer(tradable) && (tradable instanceof Trade))
            return btcFormatter.formatCoin(((Trade) tradable).getTxFee());
        else
            return btcFormatter.formatCoin(tradable.getOffer().getTxFee());
    }

    String getTradeFee(ClosedTradableListItem item) {
        if (item == null) {
            return "";
        }

        Tradable tradable = item.getTradable();
        Offer offer = tradable.getOffer();

        if (!wasMyOffer(tradable) && (tradable instanceof Trade)) {
            Trade trade = (Trade) tradable;
            Transaction takerFeeTx = btcWalletService.getTransaction(trade.getTakerFeeTxId());
            if (takerFeeTx != null && takerFeeTx.getOutputs().size() > 1) {
                // First output is fee receiver address. If its a BSQ (change) address of our own wallet its a BSQ fee
                TransactionOutput output = takerFeeTx.getOutput(0);
                Address address = output.getScriptPubKey().getToAddress(Config.baseCurrencyNetworkParameters());
                if (bsqWalletService.getWallet().findKeyFromAddress(address) != null) {
                    return bsqFormatter.formatCoinWithCode(trade.getTakerFee());
                } else {
                    return btcFormatter.formatCoinWithCode(trade.getTakerFee());
                }
            } else {
                log.warn("takerFeeTx is null or has invalid structure. takerFeeTx={}", takerFeeTx);
                return Res.get("shared.na");
            }
        } else {
            CoinFormatter formatter = offer.isCurrencyForMakerFeeBtc() ? btcFormatter : bsqFormatter;
            return formatter.formatCoinWithCode(offer.getMakerFee());
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
        return dataModel.closedTradableManager.wasMyOffer(tradable.getOffer());
    }
}
