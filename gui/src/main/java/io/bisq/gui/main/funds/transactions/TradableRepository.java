package io.bisq.gui.main.funds.transactions;

import com.google.common.collect.ImmutableSet;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;

import javax.inject.Inject;
import java.util.Set;

public class TradableRepository {
    private final OpenOfferManager openOfferManager;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;

    @Inject
    TradableRepository(OpenOfferManager openOfferManager, TradeManager tradeManager,
                       ClosedTradableManager closedTradableManager, FailedTradesManager failedTradesManager) {
        this.openOfferManager = openOfferManager;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
    }

    Set<Tradable> getAll() {
        return ImmutableSet.<Tradable>builder()
                .addAll(openOfferManager.getObservableList())
                .addAll(tradeManager.getTradableList())
                .addAll(closedTradableManager.getClosedTradables())
                .addAll(failedTradesManager.getFailedTrades())
                .build();
    }
}
