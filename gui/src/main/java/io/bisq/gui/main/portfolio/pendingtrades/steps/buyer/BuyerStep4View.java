/*
 * This file is part of bisq.
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

package io.bisq.gui.main.portfolio.pendingtrades.steps.buyer;

import io.bisq.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bisq.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import io.bisq.gui.util.FormBuilder;
import io.bisq.locale.Res;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.*;

import java.util.List;

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
            blockTextField = FormBuilder.addLabelTextField(gridPane, gridRow, Res.get("portfolio.pending.step4_buyer.blocks"), "").second;
            timeTextField = FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("portfolio.pending.step4_buyer.date")).second;
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
            return Res.get("portfolio.pending.step4_buyer.wait");
        else
            return Res.get("portfolio.pending.step4_buyer.send");
    }

    @Override
    protected String getInfoText() {
        if (model.getLockTime() > 0)
            return Res.get("portfolio.pending.step4_buyer.info");
        else
            return Res.get("portfolio.pending.step4_buyer.sending");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setInformationHeadline();
        return Res.get("portfolio.pending.step4_buyer.warn", model.getDateForOpenDispute());
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


