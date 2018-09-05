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

import bisq.core.dao.governance.proposal.compensation.CompensationProposal;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.governance.Issuance;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

//TODO case that user misses reveal phase not impl. yet

@Slf4j
public class IssuanceService {
    private final BsqStateService bsqStateService;
    private final PeriodService periodService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public IssuanceService(BsqStateService bsqStateService, PeriodService periodService) {
        this.bsqStateService = bsqStateService;
        this.periodService = periodService;
    }

    public void issueBsq(CompensationProposal compensationProposal, int chainHeight) {
        bsqStateService.getIssuanceCandidateTxOutputs().stream()
                .filter(txOutput -> isValid(txOutput, compensationProposal, periodService, chainHeight))
                .forEach(txOutput -> {
                    // We don't check atm if the output is unspent. We cannot use the bsqWallet as that would not
                    // reflect our current block state (could have been spent at later block which is valid and
                    // bsqWallet would show that spent state). We would need to support a spent status for the outputs
                    // which are interpreted as BTC (as a not yet accepted comp. request).
                    Optional<Tx> optionalTx = bsqStateService.getTx(compensationProposal.getTxId());
                    if (optionalTx.isPresent()) {
                        long amount = compensationProposal.getRequestedBsq().value;
                        Tx tx = optionalTx.get();
                        // We use key from first input
                        TxInput txInput = tx.getTxInputs().get(0);
                        String pubKey = txInput.getPubKey();
                        Issuance issuance = new Issuance(tx.getId(), chainHeight, amount, pubKey);
                        bsqStateService.addIssuance(issuance);
                        bsqStateService.addUnspentTxOutput(txOutput);

                        StringBuilder sb = new StringBuilder();
                        sb.append("\n################################################################################\n");
                        sb.append("We issued new BSQ to tx with ID ").append(txOutput.getTxId())
                                .append("\nfor compensationProposal with UID ").append(compensationProposal.getTxId())
                                .append("\n################################################################################\n");
                        log.info(sb.toString());
                    } else {
                        //TODO throw exception
                        log.error("Tx for compensation request not found. txId={}", compensationProposal.getTxId());
                    }
                });
    }

    private boolean isValid(TxOutput txOutput, CompensationProposal compensationProposal, PeriodService periodService, int chainHeight) {
        return txOutput.getTxId().equals(compensationProposal.getTxId())
                && compensationProposal.getRequestedBsq().value == txOutput.getValue()
                && compensationProposal.getBsqAddress().substring(1).equals(txOutput.getAddress())
                && periodService.isTxInPhaseAndCycle(txOutput.getTxId(), DaoPhase.Phase.PROPOSAL, chainHeight);
    }
}
