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

package bisq.core.api;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.bisq_v1.TakeOfferModel;
import bisq.core.offer.bsq_swap.BsqSwapTakeOfferModel;
import bisq.core.trade.ClosedTradableFormatter;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.User;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreTradesServiceTradeRoleTest {
    private static final String TRADE_ID = "tradeId";

    private CoreTradesService coreTradesService;
    private TradeUtil tradeUtil;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        tradeUtil = mock(TradeUtil.class);
        coreTradesService = new CoreTradesService(new CoreContext(),
                mock(CoreWalletsService.class),
                mock(BtcWalletService.class),
                mock(OfferUtil.class),
                mock(BsqSwapTradeManager.class),
                mock(ClosedTradableManager.class),
                mock(ClosedTradableFormatter.class),
                mock(FailedTradesManager.class),
                mock(TakeOfferModel.class),
                mock(BsqSwapTakeOfferModel.class),
                mock(TradeManager.class),
                tradeUtil,
                mock(User.class));

        logger = (Logger) LoggerFactory.getLogger(CoreTradesService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private Trade takerTrade(Contract contract) {
        Trade trade = mock(SellerAsTakerTrade.class);
        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.getContract()).thenReturn(contract);
        return trade;
    }

    private List<ILoggingEvent> errors() {
        return appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    // Clients call 'gettrade' right after 'takeoffer', before the taker has a contract.
    @Test
    void tradeWithoutContractHasNoRoleYetAndLogsNoError() {
        Trade trade = takerTrade(null);
        when(tradeUtil.getRole(trade)).thenThrow(new IllegalStateException(
                "could not get role because no contract was found for trade 'tradeId'"));

        assertEquals("Not Available", coreTradesService.getTradeRole(trade));
        assertEquals(List.of(), errors());
        List<ILoggingEvent> infos = appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .filter(event -> event.getFormattedMessage().contains(TRADE_ID))
                .toList();
        assertEquals(1, infos.size());
        assertNull(infos.get(0).getThrowableProxy());
    }

    @Test
    void tradeWithContractGetsItsRole() {
        Trade trade = takerTrade(mock(Contract.class));
        when(tradeUtil.getRole(trade)).thenReturn("BTC seller as taker");

        assertEquals("BTC seller as taker", coreTradesService.getTradeRole(trade));
        assertEquals(List.of(), errors());
    }

    @Test
    void failureWithContractIsStillLoggedAsError() {
        Trade trade = takerTrade(mock(Contract.class));
        when(tradeUtil.getRole(trade)).thenThrow(new IllegalStateException(
                "could not get role because no offer was found for trade 'tradeId'"));

        assertEquals("Not Available", coreTradesService.getTradeRole(trade));
        assertEquals(1, errors().size());
    }
}
