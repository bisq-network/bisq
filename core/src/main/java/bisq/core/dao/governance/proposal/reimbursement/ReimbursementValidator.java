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

package bisq.core.dao.governance.proposal.reimbursement;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ReimbursementProposal;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.Validate.notEmpty;

/**
 * Changes here can potentially break consensus!
 */
@Slf4j
public class ReimbursementValidator extends ProposalValidator implements ConsensusCritical {

    @Inject
    public ReimbursementValidator(DaoStateService daoStateService, PeriodService periodService) {
        super(daoStateService, periodService);
    }

    @Override
    public void validateDataFields(Proposal proposal) throws ProposalValidationException {
        try {
            super.validateDataFields(proposal);

            ReimbursementProposal reimbursementProposal = (ReimbursementProposal) proposal;
            String bsqAddress = reimbursementProposal.getBsqAddress();
            notEmpty(bsqAddress, "bsqAddress must not be empty");
            checkArgument(bsqAddress.substring(0, 1).equals("B"), "bsqAddress must start with B");
            reimbursementProposal.getAddress(); // throws AddressFormatException if wrong address

            Coin requestedBsq = reimbursementProposal.getRequestedBsq();
            int chainHeight = getBlockHeight(proposal);
            Coin maxReimbursementRequestAmount = ReimbursementConsensus.getMaxReimbursementRequestAmount(daoStateService, chainHeight);
            checkArgument(requestedBsq.compareTo(maxReimbursementRequestAmount) <= 0,
                    "Requested BSQ must not exceed " + (maxReimbursementRequestAmount.value / 100L) + " BSQ");
            Coin minReimbursementRequestAmount = ReimbursementConsensus.getMinReimbursementRequestAmount(daoStateService, chainHeight);
            checkArgument(requestedBsq.compareTo(minReimbursementRequestAmount) >= 0,
                    "Requested BSQ must not be less than " + (minReimbursementRequestAmount.value / 100L) + " BSQ");
        } catch (ProposalValidationException e) {
            throw e;
        } catch (Throwable throwable) {
            throw new ProposalValidationException(throwable);
        }
    }
}
