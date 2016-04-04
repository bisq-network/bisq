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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps.buyer;

import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.*;

import java.util.List;

import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;

public class BuyerStep4View extends TradeStepView {

    private TextField blockTextField;
    private TextField timeTextField;
    private final BlockChainListener blockChainListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep4View(PendingTradesViewModel model) {
        super(model);

        blockChainListener = new BlockChainListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                updateDateFromBlockHeight(block.getHeight());
            }

            @Override
            public void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) throws VerificationException {
                updateDateFromBlockHeight(model.dataModel.getBestChainHeight());
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
        updateDateFromBlockHeight(model.dataModel.getBestChainHeight());
    }

    @Override
    public void deactivate() {
        super.deactivate();

        model.removeBlockChainListener(blockChainListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addContent() {
        addTradeInfoBlock();
        if (model.getLockTime() > 0) {
            blockTextField = addLabelTextField(gridPane, gridRow, "Block(s) to wait until lock time elapsed:", "").second;
            timeTextField = addLabelTextField(gridPane, ++gridRow, "Approx. date when payout gets unlocked:").second;
            GridPane.setRowSpan(tradeInfoTitledGroupBg, 5);
        } else {
            GridPane.setRowSpan(tradeInfoTitledGroupBg, 3); //TODO should never reach
        }

        addInfoBlock();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getInfoBlockTitle() {
        if (model.getLockTime() > 0)
            return "Wait until payout lock time is over";
        else
            return "Sending payout transaction to peer"; 
    }

    @Override
    protected String getInfoText() {
        if (model.getLockTime() > 0)
            return "The payout transaction is signed and finalized by both parties.\n" +
                    "For reducing bank chargeback risks the payout transaction is blocked by a lock time.\n" +
                    "After that lock time is over the payout transaction gets published and you receive " +
                    "your bitcoin.";
        else
            return "We are sending the payout transaction to the other peer.";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setInformationHeadline();
        return "The payout transaction is still blocked by the lock time!\n" +
                "If the trade has not been completed on " +
                model.getDateForOpenDispute() +
                " the arbitrator will investigate.";
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateDateFromBlockHeight(long bestBlocKHeight) {
        if (model.getLockTime() > 0) {
            long missingBlocks = model.getLockTime() - bestBlocKHeight;

            blockTextField.setText(String.valueOf(missingBlocks));
            timeTextField.setText(model.getDateForOpenDispute());
        }
    }


}


