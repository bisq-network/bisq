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

package bisq.desktop.main.dao.economy.transactions;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import bisq.common.util.Tuple3;

import javax.inject.Inject;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelHyperlinkWithIcon;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;

@FxmlView
public class BSQTransactionsView extends ActivatableView<GridPane, Void> implements DaoStateListener {

    private final DaoFacade daoFacade;
    private final Preferences preferences;

    private int gridRow = 0;
    private TextField allTxTextField, burntFeeTxsTextField,
            utxoTextField, compensationIssuanceTxTextField,
            reimbursementIssuanceTxTextField, invalidTxsTextField, irregularTxsTextField;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BSQTransactionsView(DaoFacade daoFacade,
                                Preferences preferences) {
        this.daoFacade = daoFacade;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 2, Res.get("dao.factsAndFigures.transactions.genesis"));
        String genTxHeight = String.valueOf(daoFacade.getGenesisBlockHeight());
        String genesisTxId = daoFacade.getGenesisTxId();
        String url = preferences.getBsqBlockChainExplorer().txUrl + genesisTxId;

        GridPane.setColumnSpan(addTopLabelReadOnlyTextField(root, gridRow, Res.get("dao.factsAndFigures.transactions.genesisBlockHeight"),
                genTxHeight, Layout.FIRST_ROW_DISTANCE).third, 2);

        // TODO use addTopLabelTxIdTextField
        Tuple3<Label, HyperlinkWithIcon, VBox> tuple = addTopLabelHyperlinkWithIcon(root, ++gridRow,
                Res.get("dao.factsAndFigures.transactions.genesisTxId"), genesisTxId, url, 0);
        HyperlinkWithIcon hyperlinkWithIcon = tuple.second;
        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", genesisTxId)));

        GridPane.setColumnSpan(tuple.third, 2);


        int startRow = ++gridRow;

        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 3, Res.get("dao.factsAndFigures.transactions.txDetails"), Layout.GROUP_DISTANCE);
        titledGroupBg.getStyleClass().add("last");

        allTxTextField = addTopLabelReadOnlyTextField(root, gridRow, Res.get("dao.factsAndFigures.transactions.allTx"),
                genTxHeight, Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        utxoTextField = addTopLabelReadOnlyTextField(root, ++gridRow, Res.get("dao.factsAndFigures.transactions.utxo")).second;
        compensationIssuanceTxTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.transactions.compensationIssuanceTx")).second;
        reimbursementIssuanceTxTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.transactions.reimbursementIssuanceTx")).second;

        int columnIndex = 1;
        gridRow = startRow;

        titledGroupBg = addTitledGroupBg(root, startRow, columnIndex, 3, "", Layout.GROUP_DISTANCE);
        titledGroupBg.getStyleClass().add("last");

        burntFeeTxsTextField = addTopLabelReadOnlyTextField(root, gridRow, columnIndex,
                Res.get("dao.factsAndFigures.transactions.burntTx"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        invalidTxsTextField = addTopLabelReadOnlyTextField(root, ++gridRow, columnIndex,
                Res.get("dao.factsAndFigures.transactions.invalidTx")).second;
        irregularTxsTextField = addTopLabelReadOnlyTextField(root, ++gridRow, columnIndex,
                Res.get("dao.factsAndFigures.transactions.irregularTx")).second;
        gridRow++;

    }

    @Override
    protected void activate() {
        daoFacade.addBsqStateListener(this);

        updateWithBsqBlockChainData();
    }

    @Override
    protected void deactivate() {
        daoFacade.removeBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        updateWithBsqBlockChainData();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateWithBsqBlockChainData() {
        allTxTextField.setText(String.valueOf(daoFacade.getNumTxs()));
        utxoTextField.setText(String.valueOf(daoFacade.getUnspentTxOutputs().size()));
        compensationIssuanceTxTextField.setText(String.valueOf(daoFacade.getNumIssuanceTransactions(IssuanceType.COMPENSATION)));
        reimbursementIssuanceTxTextField.setText(String.valueOf(daoFacade.getNumIssuanceTransactions(IssuanceType.REIMBURSEMENT)));
        burntFeeTxsTextField.setText(String.valueOf(daoFacade.getBurntFeeTxs().size()));
        invalidTxsTextField.setText(String.valueOf(daoFacade.getInvalidTxs().size()));
        irregularTxsTextField.setText(String.valueOf(daoFacade.getIrregularTxs().size()));
    }
}

