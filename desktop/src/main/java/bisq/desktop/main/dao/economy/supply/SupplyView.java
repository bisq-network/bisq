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
import bisq.desktop.components.chart.ChartViewModel;
import bisq.desktop.main.dao.economy.supply.daodata.DaoChartDataModel;
import bisq.desktop.main.dao.economy.supply.daodata.DaoChartView;
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

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;

@FxmlView
public class SupplyView extends ActivatableView<GridPane, Void> implements DaoStateListener, ChartViewModel.Listener {
    private final DaoFacade daoFacade;
    private final DaoChartView daoChartView;
    // Shared model between SupplyView and RevenueChartModel
    private final DaoChartDataModel daoChartDataModel;
    private final BsqFormatter bsqFormatter;

    private TextField genesisIssueAmountTextField, compRequestIssueAmountTextField, reimbursementAmountTextField,
            totalBurntBsqTradeFeeTextField, totalLockedUpAmountTextField, totalUnlockingAmountTextField,
            totalUnlockedAmountTextField, totalConfiscatedAmountTextField, totalProofOfBurnAmountTextField;
    private int gridRow = 0;
    private long fromDate, toDate;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private SupplyView(DaoFacade daoFacade,
                       DaoChartView daoChartView,
                       DaoChartDataModel daoChartDataModel,
                       BsqFormatter bsqFormatter) {
        this.daoFacade = daoFacade;
        this.daoChartView = daoChartView;
        this.daoChartDataModel = daoChartDataModel;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        daoFacade.getTx(daoFacade.getGenesisTxId()).ifPresent(tx -> fromDate = tx.getTime());

        createChart();
        createIssuedAndBurnedFields();
        createLockedBsqFields();
    }

    @Override
    protected void activate() {
        Coin issuedAmountFromGenesis = daoFacade.getGenesisTotalSupply();
        genesisIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromGenesis));

        updateWithBsqBlockChainData();

        daoChartView.activate();
        daoChartView.addListener(this);
        daoFacade.addBsqStateListener(this);
    }

    @Override
    protected void deactivate() {
        daoChartView.removeListener(this);
        daoFacade.removeBsqStateListener(this);
        daoChartView.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ChartModel.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDateFilterChanged(long fromDate, long toDate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        updateEconomicsData();
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

    private void createChart() {
        addTitledGroupBg(root, gridRow, 2, Res.get("dao.factsAndFigures.supply.issuedVsBurnt"));
        daoChartView.initialize();

        AnchorPane chartPane = new AnchorPane();
        chartPane.getStyleClass().add("chart-pane");
        VBox chartContainer = daoChartView.getRoot();
        AnchorPane.setTopAnchor(chartContainer, 15d);
        AnchorPane.setBottomAnchor(chartContainer, 10d);
        AnchorPane.setLeftAnchor(chartContainer, 25d);
        AnchorPane.setRightAnchor(chartContainer, 10d);
        GridPane.setColumnSpan(chartPane, 2);
        GridPane.setRowIndex(chartPane, ++gridRow);
        GridPane.setMargin(chartPane, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        chartPane.getChildren().add(chartContainer);

        this.root.getChildren().add(chartPane);
    }

    private void createIssuedAndBurnedFields() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.factsAndFigures.supply.issued"), Layout.GROUP_DISTANCE);
        titledGroupBg.getStyleClass().add("last"); // hides separator as we add a second TitledGroupBg

        Tuple3<Label, TextField, VBox> genesisAmountTuple = addTopLabelReadOnlyTextField(root, gridRow,
                Res.get("dao.factsAndFigures.supply.genesisIssueAmount"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        genesisIssueAmountTextField = genesisAmountTuple.second;
        GridPane.setColumnSpan(genesisAmountTuple.third, 2);

        compRequestIssueAmountTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.supply.compRequestIssueAmount")).second;
        reimbursementAmountTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.supply.reimbursementAmount")).second;

        addTitledGroupBg(root, ++gridRow, 1, Res.get("dao.factsAndFigures.supply.burnt"), Layout.GROUP_DISTANCE_WITHOUT_SEPARATOR);

        totalBurntBsqTradeFeeTextField = addTopLabelReadOnlyTextField(root, gridRow,
                Res.get("dao.factsAndFigures.supply.bsqTradeFee"), Layout.COMPACT_FIRST_ROW_AND_COMPACT_GROUP_DISTANCE).second;

        totalProofOfBurnAmountTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
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
        updateEconomicsData();
        updateLockedTxData();
    }

    private void updateEconomicsData() {
        // We use the supplyDataProvider to get the adjusted data with static historical data as well to use the same
        // monthly scoped data.
        Coin issuedAmountFromCompRequests = Coin.valueOf(daoChartDataModel.getCompensationAmount(fromDate, getToDate()));
        compRequestIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromCompRequests));

        Coin issuedAmountFromReimbursementRequests = Coin.valueOf(daoChartDataModel.getReimbursementAmount(fromDate, getToDate()));
        reimbursementAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromReimbursementRequests));

        Coin totalBurntTradeFee = Coin.valueOf(daoChartDataModel.getBsqTradeFeeAmount(fromDate, getToDate()));
        totalBurntBsqTradeFeeTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalBurntTradeFee));

        Coin totalProofOfBurnAmount = Coin.valueOf(daoChartDataModel.getProofOfBurnAmount(fromDate, getToDate()));
        totalProofOfBurnAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalProofOfBurnAmount));
    }

    private void updateLockedTxData() {
        Coin totalLockedUpAmount = Coin.valueOf(daoFacade.getTotalLockupAmount());
        totalLockedUpAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalLockedUpAmount));

        Coin totalUnlockingAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockingTxOutputs());
        totalUnlockingAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockingAmount));

        Coin totalUnlockedAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockedTxOutputs());
        totalUnlockedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockedAmount));

        Coin totalConfiscatedAmount = Coin.valueOf(daoFacade.getTotalAmountOfConfiscatedTxOutputs());
        totalConfiscatedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalConfiscatedAmount));
    }

    private long getToDate() {
        return toDate > 0 ? toDate : System.currentTimeMillis();
    }
}
