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

package bisq.desktop.main.dao.economy.supply;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.economy.supply.dao.DaoChartView;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyLongProperty;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;

@FxmlView
public class SupplyView extends ActivatableView<GridPane, Void> implements DaoStateListener {
    private final DaoFacade daoFacade;
    private final DaoChartView daoChartView;
    private final BsqFormatter bsqFormatter;

    private TextField genesisIssueAmountTextField, compensationAmountTextField, reimbursementAmountTextField,
            bsqTradeFeeAmountTextField, totalLockedUpAmountTextField, totalUnlockingAmountTextField,
            totalUnlockedAmountTextField, totalConfiscatedAmountTextField, proofOfBurnAmountTextField;
    private int gridRow = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private SupplyView(DaoFacade daoFacade,
                       DaoChartView daoChartView,
                       BsqFormatter bsqFormatter) {
        this.daoFacade = daoFacade;
        this.daoChartView = daoChartView;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        createDaoChart();
        createIssuedAndBurnedFields();
        createLockedBsqFields();
    }

    @Override
    protected void activate() {
        daoFacade.addBsqStateListener(this);

        compensationAmountTextField.textProperty().bind(Bindings.createStringBinding(
                () -> getFormattedValue(daoChartView.compensationAmountProperty()),
                daoChartView.compensationAmountProperty()));
        reimbursementAmountTextField.textProperty().bind(Bindings.createStringBinding(
                () -> getFormattedValue(daoChartView.reimbursementAmountProperty()),
                daoChartView.reimbursementAmountProperty()));
        bsqTradeFeeAmountTextField.textProperty().bind(Bindings.createStringBinding(
                () -> getFormattedValue(daoChartView.bsqTradeFeeAmountProperty()),
                daoChartView.bsqTradeFeeAmountProperty()));
        proofOfBurnAmountTextField.textProperty().bind(Bindings.createStringBinding(
                () -> getFormattedValue(daoChartView.proofOfBurnAmountProperty()),
                daoChartView.proofOfBurnAmountProperty()));

        Coin issuedAmountFromGenesis = daoFacade.getGenesisTotalSupply();
        genesisIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromGenesis));
        updateWithBsqBlockChainData();
    }

    @Override
    protected void deactivate() {
        daoFacade.removeBsqStateListener(this);

        compensationAmountTextField.textProperty().unbind();
        reimbursementAmountTextField.textProperty().unbind();
        bsqTradeFeeAmountTextField.textProperty().unbind();
        proofOfBurnAmountTextField.textProperty().unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        updateWithBsqBlockChainData();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createDaoChart() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 2, Res.get("dao.factsAndFigures.supply.issuedVsBurnt"));
        titledGroupBg.getStyleClass().add("last"); // hides separator as we add a second TitledGroupBg

        daoChartView.initialize();
        VBox chartContainer = daoChartView.getRoot();

        AnchorPane chartPane = new AnchorPane();
        chartPane.getStyleClass().add("chart-pane");
        AnchorPane.setTopAnchor(chartContainer, 15d);
        AnchorPane.setBottomAnchor(chartContainer, 0d);
        AnchorPane.setLeftAnchor(chartContainer, 25d);
        AnchorPane.setRightAnchor(chartContainer, 10d);
        GridPane.setColumnSpan(chartPane, 2);
        GridPane.setRowIndex(chartPane, ++gridRow);
        GridPane.setMargin(chartPane, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        chartPane.getChildren().add(chartContainer);

        root.getChildren().add(chartPane);
    }

    private void createIssuedAndBurnedFields() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.factsAndFigures.supply.issued"), Layout.FLOATING_LABEL_DISTANCE);
        titledGroupBg.getStyleClass().add("last"); // hides separator as we add a second TitledGroupBg

        Tuple3<Label, TextField, VBox> genesisAmountTuple = addTopLabelReadOnlyTextField(root, gridRow,
                Res.get("dao.factsAndFigures.supply.genesisIssueAmount"), Layout.COMPACT_FIRST_ROW_AND_COMPACT_GROUP_DISTANCE);
        genesisIssueAmountTextField = genesisAmountTuple.second;
        GridPane.setColumnSpan(genesisAmountTuple.third, 2);

        compensationAmountTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.supply.compRequestIssueAmount")).second;
        reimbursementAmountTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.supply.reimbursementAmount")).second;

        addTitledGroupBg(root, ++gridRow, 1, Res.get("dao.factsAndFigures.supply.burnt"), Layout.GROUP_DISTANCE_WITHOUT_SEPARATOR);

        bsqTradeFeeAmountTextField = addTopLabelReadOnlyTextField(root, gridRow,
                Res.get("dao.factsAndFigures.supply.bsqTradeFee"), Layout.COMPACT_FIRST_ROW_AND_COMPACT_GROUP_DISTANCE).second;

        proofOfBurnAmountTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.supply.proofOfBurn"), Layout.COMPACT_FIRST_ROW_AND_COMPACT_GROUP_DISTANCE).second;
    }

    private void createLockedBsqFields() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 2, Res.get("dao.factsAndFigures.supply.locked"), Layout.GROUP_DISTANCE);
        titledGroupBg.getStyleClass().add("last");

        totalLockedUpAmountTextField = addTopLabelReadOnlyTextField(root, gridRow,
                Res.get("dao.factsAndFigures.supply.totalLockedUpAmount"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        totalUnlockingAmountTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.supply.totalUnlockingAmount"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;

        totalUnlockedAmountTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.supply.totalUnlockedAmount")).second;
        totalConfiscatedAmountTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.supply.totalConfiscatedAmount")).second;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateWithBsqBlockChainData() {
        Coin totalLockedUpAmount = Coin.valueOf(daoFacade.getTotalLockupAmount());
        totalLockedUpAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalLockedUpAmount));

        Coin totalUnlockingAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockingTxOutputs());
        totalUnlockingAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockingAmount));

        Coin totalUnlockedAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockedTxOutputs());
        totalUnlockedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockedAmount));

        Coin totalConfiscatedAmount = Coin.valueOf(daoFacade.getTotalAmountOfConfiscatedTxOutputs());
        totalConfiscatedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalConfiscatedAmount));
    }

    private String getFormattedValue(ReadOnlyLongProperty property) {
        return bsqFormatter.formatAmountWithGroupSeparatorAndCode(Coin.valueOf(property.get()));
    }
}
