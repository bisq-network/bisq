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

package bisq.core.btc.wallet;

import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputListService;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.RegTestParams;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BsqCoinSelectorsCheckpointTest {
    private final DaoStateService daoStateService = mock(DaoStateService.class);
    private final DaoStateMonitoringService monitor = mock(DaoStateMonitoringService.class);
    private final UnconfirmedBsqChangeOutputListService changeOutputListService =
            mock(UnconfirmedBsqChangeOutputListService.class);

    @Test
    void checkpointFailureBlocksOtherwiseSpendableBsq() {
        BsqCoinSelector selector = new BsqCoinSelector(daoStateService, monitor, changeOutputListService);
        TransactionOutput output = confirmedOutput();
        when(daoStateService.isTxOutputSpendable(any(TxOutputKey.class))).thenReturn(true);
        assertEquals(output.getValue(), selector.select(output.getValue(), List.of(output)).valueGathered);

        when(monitor.isCheckpointFailed()).thenReturn(true);

        assertTrue(selector.select(output.getValue(), List.of(output)).gathered.isEmpty());
    }

    @Test
    void checkpointFailureAlsoBlocksOwnUnconfirmedBsqChange() {
        BsqCoinSelector selector = new BsqCoinSelector(daoStateService, monitor, changeOutputListService);
        TransactionOutput output = confirmedOutput();
        output.getParentTransaction().getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        output.getParentTransaction().getConfidence().setSource(TransactionConfidence.Source.SELF);
        when(changeOutputListService.hasTransactionOutput(output)).thenReturn(true);
        assertEquals(output.getValue(), selector.select(output.getValue(), List.of(output)).valueGathered);

        when(monitor.isCheckpointFailed()).thenReturn(true);

        assertTrue(selector.select(output.getValue(), List.of(output)).gathered.isEmpty());
    }

    @Test
    void checkpointFailurePreventsAbsentIssuanceFromBeingSelectedAsBtc() {
        NonBsqCoinSelector selector = nonBsqCoinSelector();
        TransactionOutput output = confirmedOutput();
        assertEquals(output.getValue(), selector.select(output.getValue(), List.of(output)).valueGathered);

        when(monitor.isCheckpointFailed()).thenReturn(true);

        assertTrue(selector.select(output.getValue(), List.of(output)).gathered.isEmpty());
    }

    @Test
    void checkpointFailurePreventsRejectedIssuanceFromBeingSelectedAsBtc() {
        NonBsqCoinSelector selector = nonBsqCoinSelector();
        TransactionOutput output = confirmedOutput();
        when(daoStateService.existsTxOutput(any())).thenReturn(true);
        when(daoStateService.isRejectedIssuanceOutput(any())).thenReturn(true);
        assertEquals(output.getValue(), selector.select(output.getValue(), List.of(output)).valueGathered);

        when(monitor.isCheckpointFailed()).thenReturn(true);

        assertTrue(selector.select(output.getValue(), List.of(output)).gathered.isEmpty());
    }

    private NonBsqCoinSelector nonBsqCoinSelector() {
        NonBsqCoinSelector selector = new NonBsqCoinSelector(daoStateService, monitor);
        selector.setPreferences(mock(Preferences.class));
        return selector;
    }

    private TransactionOutput confirmedOutput() {
        Context.propagate(new Context(RegTestParams.get()));
        Transaction transaction = new Transaction(RegTestParams.get());
        transaction.addOutput(Coin.valueOf(10_000), new ECKey());
        transaction.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        return transaction.getOutput(0);
    }
}
