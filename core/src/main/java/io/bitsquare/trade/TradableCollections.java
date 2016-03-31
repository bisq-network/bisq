package io.bitsquare.trade;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletService;
import io.bitsquare.trade.offer.OpenOfferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TradableCollections {
    private static final Logger log = LoggerFactory.getLogger(TradableCollections.class);

    public static List<AddressEntry> getAddressEntriesForAvailableBalance(OpenOfferManager openOfferManager, TradeManager tradeManager, WalletService walletService) {
        Set<String> reservedTrades = getNotCompletedTradableItems(openOfferManager, tradeManager).stream()
                .map(tradable -> tradable.getOffer().getId())
                .collect(Collectors.toSet());

        List<AddressEntry> list = new ArrayList<>();
        list.addAll(walletService.getAddressEntryList().stream()
                .filter(e -> walletService.getBalanceForAddress(e.getAddress()).isPositive())
                .filter(e -> !reservedTrades.contains(e.getOfferId()))
                .collect(Collectors.toList()));
        return list;
    }

    public static Set<Tradable> getNotCompletedTradableItems(OpenOfferManager openOfferManager, TradeManager tradeManager) {
        return Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream())
                .filter(tradable -> !(tradable instanceof Trade) || ((Trade) tradable).getState().getPhase() != Trade.Phase.PAYOUT_PAID)
                .collect(Collectors.toSet());
    }
}
