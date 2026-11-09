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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer;

import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuyerVerifiesPreparedDelayedPayoutTxTest {
    private static final int BURNING_MAN_SELECTION_HEIGHT = 900_000;

    // Makers up to v1.10.11 accepted a trade tx fee down to 250 sat, and their trade can reach this check after the
    // buyer updated, while a new take offer request needs 424 sat. The task fails later in any case, as this test
    // sets up no prepared deposit tx.
    @ParameterizedTest
    @CsvSource({"249, true", "250, false", "423, false", "424, false"})
    void checksTheTradeTxFeeOfAnExistingTradeAgainstTheMinimumOfEarlierMakers(long tradeTxFee,
                                                                               boolean refusedForTheFee) {
        Trade trade = trade(tradeTxFee);

        new BuyerVerifiesPreparedDelayedPayoutTx(taskRunner(), trade).run();

        ArgumentCaptor<String> errorMessage = ArgumentCaptor.forClass(String.class);
        verify(trade).setErrorMessage(errorMessage.capture());
        assertEquals(refusedForTheFee, errorMessage.getValue().contains("tradeTxFee too low"), errorMessage.getValue());
    }

    private static Trade trade(long tradeTxFee) {
        // At 2 sat/vbyte the expected fee is 424 sat, and the factor of 4 accepts 106 to 1696 sat
        FeeService feeService = mock(FeeService.class);
        when(feeService.getTxFeePerVbyte()).thenReturn(Coin.valueOf(2));
        DelayedPayoutTxReceiverService delayedPayoutTxReceiverService = mock(DelayedPayoutTxReceiverService.class);
        when(delayedPayoutTxReceiverService.getBurningManSelectionHeight()).thenReturn(BURNING_MAN_SELECTION_HEIGHT);

        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.getFeeService()).thenReturn(feeService);
        when(processModel.getDelayedPayoutTxReceiverService()).thenReturn(delayedPayoutTxReceiverService);
        when(processModel.getBurningManSelectionHeight()).thenReturn(BURNING_MAN_SELECTION_HEIGHT);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));

        Trade trade = mock(Trade.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getTradeTxFeeAsLong()).thenReturn(tradeTxFee);
        when(trade.getId()).thenReturn("trade-id");
        return trade;
    }

    @SuppressWarnings("unchecked")
    private static TaskRunner<Trade> taskRunner() {
        return mock(TaskRunner.class);
    }
}
