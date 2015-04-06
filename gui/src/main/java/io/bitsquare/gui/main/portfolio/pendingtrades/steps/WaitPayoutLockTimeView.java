/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.portfolio.pendingtrades.steps;

import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.util.Layout;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.BlockChainListener;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;

import java.util.List;

import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.ComponentBuilder.*;

public class WaitPayoutLockTimeView extends TradeStepDetailsView {
    private static final Logger log = LoggerFactory.getLogger(WaitPayoutLockTimeView.class);

    private final BlockChainListener blockChainListener;

    private TextField blockTextField;
    private Label infoLabel;
    private TextField timeTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public WaitPayoutLockTimeView(PendingTradesViewModel model) {
        super(model);

        blockChainListener = new BlockChainListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                setLockTime(block.getHeight());
            }

            @Override
            public void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) throws VerificationException {
                setLockTime(model.getBestChainHeight());
            }

            @Override
            public boolean isTransactionRelevant(Transaction tx) throws ScriptException {
                return false;
            }

            @Override
            public void receiveFromBlock(Transaction tx, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset) throws
                    VerificationException {
            }

            @Override
            public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset)
                    throws VerificationException {
                return false;
            }
        };
    }

    @Override
    public void activate() {
        super.activate();

        model.addBlockChainListener(blockChainListener);
        setLockTime(model.getBestChainHeight());
    }

    @Override
    public void deactivate() {
        super.deactivate();

        model.removeBlockChainListener(blockChainListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setInfoLabelText(String text) {
        if (infoLabel != null)
            infoLabel.setText(text);
    }

    private void setLockTime(int bestBlocKHeight) {
        long missingBlocks = model.getLockTime() - (long) bestBlocKHeight;
        blockTextField.setText(String.valueOf(missingBlocks));
        timeTextField.setText(model.getUnlockDate(missingBlocks));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build view
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void buildGridEntries() {
        getAndAddTitledGroupBg(gridPane, gridRow, 2, "Payout transaction lock time");
        blockTextField = getAndAddLabelTextFieldPair(gridPane, gridRow++, "Block(s) to wait until unlock:", Layout.FIRST_ROW_DISTANCE).textField;
        timeTextField = getAndAddLabelTextFieldPair(gridPane, gridRow++, "Approx. date when payout gets unlocked:").textField;

        getAndAddTitledGroupBg(gridPane, gridRow, 1, "Information", Layout.GROUP_DISTANCE);
        infoLabel = getAndAddInfoLabel(gridPane, gridRow++, Layout.FIRST_ROW_AND_GROUP_DISTANCE);
    }
}


