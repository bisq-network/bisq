package io.bitsquare.trade;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletService;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

public class TradableHelper {
    private static final Logger log = LoggerFactory.getLogger(TradableHelper.class);

    public static Stream<AddressEntry> getAddressEntriesForAvailableBalanceStream(WalletService walletService) {
        Stream<AddressEntry> availableOrPayout = Stream.concat(walletService.getAddressEntries(AddressEntry.Context.TRADE_PAYOUT).stream(), walletService.getFundedAvailableAddressEntries().stream());
        Stream<AddressEntry> available = Stream.concat(availableOrPayout, walletService.getAddressEntries(AddressEntry.Context.ARBITRATOR).stream());
        available = Stream.concat(available, walletService.getAddressEntries(AddressEntry.Context.OFFER_FUNDING).stream());
        return available
                .filter(addressEntry -> walletService.getBalanceForAddress(addressEntry.getAddress()).isPositive());
    }

    public static Stream<Trade> getLockedTradeStream(TradeManager tradeManager) {
        return tradeManager.getTrades().stream()
                .filter(trade -> trade.getState().getPhase().ordinal() >= Trade.Phase.DEPOSIT_PAID.ordinal() &&
                        trade.getState().getPhase().ordinal() < Trade.Phase.PAYOUT_PAID.ordinal());
    }

    public static AddressEntry getLockedTradeAddressEntry(Trade trade, WalletService walletService) {
        return walletService.getOrCreateAddressEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
    }

    public static Coin getReservedBalance(Tradable tradable, WalletService walletService) {
        return walletService.getBalanceForAddress(walletService.getOrCreateAddressEntry(tradable.getId(), AddressEntry.Context.RESERVED_FOR_TRADE).getAddress());
    }
}
