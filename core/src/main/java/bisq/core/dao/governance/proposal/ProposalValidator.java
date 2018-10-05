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

package bisq.core.dao.governance.proposal;

import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.governance.proposal.compensation.CompensationProposal;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static org.apache.commons.lang3.Validate.notEmpty;

@Slf4j
public class ProposalValidator {

    protected final BsqStateService bsqStateService;
    protected final PeriodService periodService;

    @Inject
    public ProposalValidator(BsqStateService bsqStateService, PeriodService periodService) {
        this.bsqStateService = bsqStateService;
        this.periodService = periodService;
    }

    public boolean areDataFieldsValid(Proposal proposal) {
        try {
            validateDataFields(proposal);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }

    public void validateDataFields(Proposal proposal) throws ValidationException {
        try {
            notEmpty(proposal.getName(), "name must not be empty");
            notEmpty(proposal.getLink(), "link must not be empty");
        } catch (Throwable throwable) {
            throw new ValidationException(throwable);
        }
    }

    public boolean isValidOrUnconfirmed(Proposal proposal) {
        return isValid(proposal, true);
    }

    public boolean isValidAndConfirmed(Proposal proposal) {
        return isValid(proposal, false);
    }

    public boolean isTxTypeValid(Proposal proposal) {
        String txId = proposal.getTxId();
        if (txId == null || txId.equals("")) {
            log.warn("txId must be set. proposal.getTxId()={}", proposal.getTxId());
            return false;
        }
        Optional<TxType> optionalTxType = bsqStateService.getOptionalTxType(txId);
        boolean present = optionalTxType.filter(txType -> txType == proposal.getTxType()).isPresent();
        if (!present)
            log.debug("optionalTxType not present for proposal {}" + proposal);
        return present;
    }

    private boolean isValid(Proposal proposal, boolean allowUnconfirmed) {
        if (!areDataFieldsValid(proposal)) {
            log.warn("proposal data fields are invalid. proposal={}", proposal);
            return false;
        }

        String txId = proposal.getTxId();
        if (txId == null || txId.equals("")) {
            log.warn("txId must be set. proposal.getTxId()={}", proposal.getTxId());
            return false;
        }

        Optional<Tx> optionalTx = bsqStateService.getTx(txId);
        boolean isTxConfirmed = optionalTx.isPresent();
        int chainHeight = bsqStateService.getChainHeight();

        if (isTxConfirmed) {
            int txHeight = optionalTx.get().getBlockHeight();
            if (!periodService.isTxInCorrectCycle(txHeight, chainHeight)) {
                log.debug("Tx is not in current cycle. proposal.getTxId()={}", proposal.getTxId());
                return false;
            }
            if (!periodService.isInPhase(txHeight, DaoPhase.Phase.PROPOSAL)) {
                log.debug("Tx is not in PROPOSAL phase. proposal.getTxId()={}", proposal.getTxId());
                return false;
            }
            if (proposal instanceof CompensationProposal) {
                if (optionalTx.get().getTxType() != TxType.COMPENSATION_REQUEST) {
                    log.error("TxType is not PROPOSAL. proposal.getTxId()={}", proposal.getTxId());
                    return false;
                }
            } else {
                if (optionalTx.get().getTxType() != TxType.PROPOSAL) {
                    log.error("TxType is not PROPOSAL. proposal.getTxId()={}", proposal.getTxId());
                    return false;
                }
            }

            return true;
        } else if (allowUnconfirmed) {
            // We want to show own unconfirmed proposals in the active proposals list.
            boolean inPhase = periodService.isInPhase(chainHeight, DaoPhase.Phase.PROPOSAL);
            if (inPhase)
                log.debug("proposal is unconfirmed and we are in proposal phase: txId={}", txId);
            return inPhase;
        } else {
            return false;
        }
    }
}
