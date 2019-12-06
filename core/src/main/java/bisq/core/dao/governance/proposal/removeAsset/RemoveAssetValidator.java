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

package bisq.core.dao.governance.proposal.removeAsset;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.Validate.notEmpty;

/**
 * Changes here can potentially break consensus!
 */
@Slf4j
public class RemoveAssetValidator extends ProposalValidator implements ConsensusCritical {

    @Inject
    public RemoveAssetValidator(DaoStateService daoStateService, PeriodService periodService) {
        super(daoStateService, periodService);
    }

    @Override
    public void validateDataFields(Proposal proposal) throws ProposalValidationException {
        try {
            super.validateDataFields(proposal);

            RemoveAssetProposal removeAssetProposal = (RemoveAssetProposal) proposal;
            notEmpty(removeAssetProposal.getTickerSymbol(), "TickerSymbol must not be empty");

            // We want to avoid that someone causes damage by inserting a super long string. Real ticker symbols
            // are usually very short but we don't want to add additional restrictions here.
            checkArgument(removeAssetProposal.getTickerSymbol().length() <= 100, "TickerSymbol must not exceed 100 chars");
        } catch (ProposalValidationException e) {
            throw e;
        } catch (Throwable throwable) {
            throw new ProposalValidationException(throwable);
        }
    }
}
