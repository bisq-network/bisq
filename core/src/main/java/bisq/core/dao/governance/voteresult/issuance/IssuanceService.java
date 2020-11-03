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

package bisq.core.dao.governance.voteresult.issuance;

import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.IssuanceProposal;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.ReimbursementProposal;

import bisq.common.util.MathUtils;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;


@Slf4j
public class IssuanceService {
    private final DaoStateService daoStateService;
    private final PeriodService periodService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public IssuanceService(DaoStateService daoStateService, PeriodService periodService) {
        this.daoStateService = daoStateService;
        this.periodService = periodService;
    }

    public void issueBsq(IssuanceProposal issuanceProposal, int chainHeight) {
        daoStateService.getIssuanceCandidateTxOutputs().stream()
                .filter(txOutput -> isValid(txOutput, issuanceProposal, periodService, chainHeight))
                .forEach(txOutput -> {
                    IssuanceType issuanceType = IssuanceType.UNDEFINED;
                    if (issuanceProposal instanceof CompensationProposal) {
                        issuanceType = IssuanceType.COMPENSATION;
                    } else if (issuanceProposal instanceof ReimbursementProposal) {
                        issuanceType = IssuanceType.REIMBURSEMENT;
                    }
                    checkArgument(issuanceType != IssuanceType.UNDEFINED, "issuanceType must not be undefined");

                    // We don't check atm if the output is unspent. We cannot use the bsqWallet as that would not
                    // reflect our current block state (could have been spent at later block which is valid and
                    // bsqWallet would show that spent state). We would need to support a spent status for the outputs
                    // which are interpreted as BTC (as a not yet accepted comp. request).
                    Optional<Tx> optionalTx = daoStateService.getTx(issuanceProposal.getTxId());
                    checkArgument(optionalTx.isPresent(), "optionalTx must be present");
                    long amount = issuanceProposal.getRequestedBsq().value;
                    Tx tx = optionalTx.get();
                    // We use key from first input
                    TxInput txInput = tx.getTxInputs().get(0);
                    String pubKey = txInput.getPubKey();
                    Issuance issuance = new Issuance(tx.getId(), chainHeight, amount, pubKey, issuanceType);
                    daoStateService.addIssuance(issuance);
                    daoStateService.addUnspentTxOutput(txOutput);

                    @SuppressWarnings("StringBufferReplaceableByString")
                    StringBuilder sb = new StringBuilder();
                    sb.append("\n################################################################################\n");
                    sb.append("We issued new BSQ to tx with ID ").append(txOutput.getTxId())
                            .append("\nIssued BSQ: ").append(MathUtils.scaleDownByPowerOf10(amount, 2))
                            .append("\nIssuance type: ").append(issuanceType.name())
                            .append("\n################################################################################\n");
                    log.info(sb.toString());
                });
    }

    private boolean isValid(TxOutput txOutput, IssuanceProposal issuanceProposal, PeriodService periodService, int chainHeight) {
        return txOutput.getTxId().equals(issuanceProposal.getTxId())
                && issuanceProposal.getRequestedBsq().value == txOutput.getValue()
                && issuanceProposal.getBsqAddress().substring(1).equals(txOutput.getAddress())
                && periodService.isTxInPhaseAndCycle(txOutput.getTxId(), DaoPhase.Phase.PROPOSAL, chainHeight);
    }
}
