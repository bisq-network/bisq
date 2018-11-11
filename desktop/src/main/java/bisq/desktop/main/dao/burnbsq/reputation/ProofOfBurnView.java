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

package bisq.desktop.main.dao.burnbsq.reputation;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.layout.GridPane;

@FxmlView
public class ProofOfBurnView extends ActivatableView<GridPane, Void> implements BsqBalanceListener {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ProofOfBurnView(BsqFormatter bsqFormatter,
                            BsqWalletService bsqWalletService,
                            BsqValidator bsqValidator,
                            DaoFacade daoFacade,
                            Preferences preferences) {
    }

    @Override
    public void initialize() {

    }

    @Override
    protected void activate() {

        updateList();
    }

    @Override
    protected void deactivate() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBalanceListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createListeners() {
    }

    private void updateList() {
    }

    private void updateButtonState() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns() {

    }
}
