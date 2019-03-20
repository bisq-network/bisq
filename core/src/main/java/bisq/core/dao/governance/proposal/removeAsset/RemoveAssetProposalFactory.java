/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.proposal.removeAsset;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.governance.proposal.BaseProposalFactory;
import bisq.core.dao.governance.proposal.ProposalValidationException;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;

import bisq.asset.Asset;

import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import java.util.HashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates RemoveAssetProposal and transaction.
 */
@Slf4j
public class RemoveAssetProposalFactory extends BaseProposalFactory<RemoveAssetProposal> {
    private Asset asset;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public RemoveAssetProposalFactory(BsqWalletService bsqWalletService,
                                      BtcWalletService btcWalletService,
                                      DaoStateService daoStateService,
                                      RemoveAssetValidator proposalValidator) {
        super(bsqWalletService,
                btcWalletService,
                daoStateService,
                proposalValidator);
    }

    public ProposalWithTransaction createProposalWithTransaction(String name,
                                                                 String link,
                                                                 Asset asset)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        this.asset = asset;

        return super.createProposalWithTransaction(name, link);
    }

    @Override
    protected RemoveAssetProposal createProposalWithoutTxId() {
        return new RemoveAssetProposal(
                name,
                link,
                asset.getTickerSymbol(),
                new HashMap<>());
    }
}
