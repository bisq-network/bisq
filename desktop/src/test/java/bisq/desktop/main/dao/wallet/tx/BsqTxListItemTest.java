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

package bisq.desktop.main.dao.wallet.tx;

import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.payment.payload.PaymentMethod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class BsqTxListItemTest {

    @Test
    public void getTxTypeForExport_returnsBsqSwap_forBsqSwapTx() {
        BsqTxListItem item = spy(new BsqTxListItem());
        doReturn(true).when(item).isBsqSwapTx();

        assertEquals(PaymentMethod.BSQ_SWAP_ID, item.getTxTypeForExport());
    }

    @Test
    public void getTxTypeForExport_returnsRegularTxType_forNonBsqSwapTx() {
        BsqTxListItem item = spy(new BsqTxListItem());
        doReturn(false).when(item).isBsqSwapTx();
        doReturn(TxType.PAY_TRADE_FEE).when(item).getTxType();
        doReturn(false).when(item).isCompensationIssuanceTx(TxType.PAY_TRADE_FEE);
        doReturn(false).when(item).isReimbursementIssuanceTx(TxType.PAY_TRADE_FEE);

        assertEquals("PAY_TRADE_FEE", item.getTxTypeForExport());
    }

    @Test
    public void getTxTypeForExport_returnsCompensationIssuance_forCompensationIssuanceTx() {
        BsqTxListItem item = spy(new BsqTxListItem());
        doReturn(false).when(item).isBsqSwapTx();
        doReturn(TxType.COMPENSATION_REQUEST).when(item).getTxType();
        doReturn(true).when(item).isCompensationIssuanceTx(TxType.COMPENSATION_REQUEST);

        assertEquals(getIssuanceTypeForExport(IssuanceType.COMPENSATION), item.getTxTypeForExport());
    }

    @Test
    public void getTxTypeForExport_returnsReimbursementIssuance_forReimbursementIssuanceTx() {
        BsqTxListItem item = spy(new BsqTxListItem());
        doReturn(false).when(item).isBsqSwapTx();
        doReturn(TxType.REIMBURSEMENT_REQUEST).when(item).getTxType();
        doReturn(false).when(item).isCompensationIssuanceTx(TxType.REIMBURSEMENT_REQUEST);
        doReturn(true).when(item).isReimbursementIssuanceTx(TxType.REIMBURSEMENT_REQUEST);

        assertEquals(getIssuanceTypeForExport(IssuanceType.REIMBURSEMENT), item.getTxTypeForExport());
    }

    private static String getIssuanceTypeForExport(IssuanceType issuanceType) {
        return "ISSUANCE_FROM_" + issuanceType.name() + "_REQUEST";
    }
}
