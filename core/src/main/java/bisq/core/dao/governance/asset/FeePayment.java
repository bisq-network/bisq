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

package bisq.core.dao.governance.asset;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;

import java.util.Optional;

import lombok.Value;

@Value
public class FeePayment {
    private final String txId;
    private final long fee;

    FeePayment(String txId, long fee) {
        this.txId = txId;
        this.fee = fee;
    }

    public long daysCoveredByFee(long bsqFeePerDay) {
        return bsqFeePerDay > 0 ? fee / bsqFeePerDay : 0;
    }

    public Optional<Integer> getPassedDays(DaoStateService daoStateService) {
        Optional<Tx> optionalTx = daoStateService.getTx(txId);
        if (optionalTx.isPresent()) {
            int passedBlocks = daoStateService.getChainHeight() - optionalTx.get().getBlockHeight();
            return Optional.of(passedBlocks / 144);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "FeePayment{" +
                "\n     txId='" + txId + '\'' +
                ",\n     fee=" + fee +
                "\n}";
    }
}
