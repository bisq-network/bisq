package io.bitsquare.trade;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TradableHelper {
    private static final Logger log = LoggerFactory.getLogger(TradableHelper.class);

    public static List<AddressEntry> getAddressEntriesForAvailableBalance(OpenOfferManager openOfferManager, TradeManager tradeManager, WalletService walletService) {
        Set<String> reservedTradeIds = getNotCompletedTradableItems(openOfferManager, tradeManager).stream()
                .map(tradable -> tradable.getOffer().getId())
                .collect(Collectors.toSet());

        return walletService.getAddressEntryList().stream()
                .filter(e -> walletService.getBalanceForAddress(e.getAddress()).isPositive())
                .filter(e -> !reservedTradeIds.contains(e.getOfferId()))
                .collect(Collectors.toList());
    }

    public static Set<Tradable> getNotCompletedTradableItems(OpenOfferManager openOfferManager, TradeManager tradeManager) {
        return Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream())
                .filter(tradable -> !(tradable instanceof Trade) || ((Trade) tradable).getState().getPhase() != Trade.Phase.PAYOUT_PAID)
                .collect(Collectors.toSet());
    }

    public static Coin getBalanceInOpenOffer(OpenOffer openOffer) {
        Offer offer = openOffer.getOffer();
        Coin balance = FeePolicy.getSecurityDeposit().add(FeePolicy.getFeePerKb());
        // For the seller we add the trade amount
        if (offer.getDirection() == Offer.Direction.SELL)
            balance = balance.add(offer.getAmount());

        return balance;
    }

    public static Coin getBalanceInTrade(Trade trade, WalletService walletService) {
        AddressEntry addressEntry = walletService.getTradeAddressEntry(trade.getId());
        Coin balance = FeePolicy.getSecurityDeposit().add(FeePolicy.getFeePerKb());
        // For the seller we add the trade amount
        if (trade.getContract() != null &&
                trade.getTradeAmount() != null &&
                trade.getContract().getSellerPayoutAddressString().equals(addressEntry.getAddressString()))
            balance = balance.add(trade.getTradeAmount());
        return balance;
    }

    public static Coin getAvailableBalance(AddressEntry addressEntry, WalletService walletService,
                                           OpenOfferManager openOfferManager, TradeManager tradeManager,
                                           ClosedTradableManager closedTradableManager,
                                           FailedTradesManager failedTradesManager) {
        Coin balance;
        Coin totalBalance = walletService.getBalanceForAddress(addressEntry.getAddress());
        String id = addressEntry.getOfferId();
        Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(id);
        Optional<Trade> tradeOptional = tradeManager.getTradeById(id);
        Optional<Tradable> closedTradableOptional = closedTradableManager.getTradableById(id);
        Optional<Trade> failedTradesOptional = failedTradesManager.getTradeById(id);

        if (openOfferOptional.isPresent()) {
            balance = totalBalance.subtract(TradableHelper.getBalanceInOpenOffer(openOfferOptional.get()));
        } else if (tradeOptional.isPresent()) {
            Trade trade = tradeOptional.get();
            if (trade.getState().getPhase() != Trade.Phase.PAYOUT_PAID)
                balance = totalBalance.subtract(TradableHelper.getBalanceInTrade(trade, walletService));
            else
                balance = totalBalance;
        } else if (closedTradableOptional.isPresent()) {
            Tradable tradable = closedTradableOptional.get();
            Coin balanceInTrade = Coin.ZERO;
            if (tradable instanceof OpenOffer)
                balanceInTrade = TradableHelper.getBalanceInOpenOffer((OpenOffer) tradable);
            else if (tradable instanceof Trade)
                balanceInTrade = TradableHelper.getBalanceInTrade((Trade) tradable, walletService);
            balance = totalBalance.subtract(balanceInTrade);
        } else if (failedTradesOptional.isPresent()) {
            balance = totalBalance.subtract(TradableHelper.getBalanceInTrade(failedTradesOptional.get(), walletService));
        } else {
            balance = totalBalance;
        }
        return balance;
    }

    public static Coin getReservedBalance(Tradable tradable, WalletService walletService) {
        AddressEntry addressEntry = walletService.getTradeAddressEntry(tradable.getId());
        Coin balance = walletService.getBalanceForAddress(addressEntry.getAddress());
        if (tradable instanceof Trade) {
            Trade trade = (Trade) tradable;
            if (trade.getState().getPhase().ordinal() < Trade.Phase.PAYOUT_PAID.ordinal())
                balance = TradableHelper.getBalanceInTrade(trade, walletService);
        } else if (tradable instanceof OpenOffer) {
            balance = TradableHelper.getBalanceInOpenOffer((OpenOffer) tradable);
        }
        return balance;
    }
}
