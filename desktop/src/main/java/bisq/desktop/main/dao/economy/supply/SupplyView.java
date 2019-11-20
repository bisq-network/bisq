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
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Side;

import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;



import java.sql.Date;

@FxmlView
public class SupplyView extends ActivatableView<GridPane, Void> implements DaoStateListener {

    private static final String MONTH = "month";
    private static final String DAY = "day";

    private final DaoFacade daoFacade;
    private DaoStateService daoStateService;
    private final BsqFormatter bsqFormatter;

    private int gridRow = 0;
    private TextField genesisIssueAmountTextField, compRequestIssueAmountTextField, reimbursementAmountTextField,
            totalBurntFeeAmountTextField, totalLockedUpAmountTextField, totalUnlockingAmountTextField,
            totalUnlockedAmountTextField, totalConfiscatedAmountTextField, totalAmountOfInvalidatedBsqTextField;
    private XYChart.Series<Number, Number> seriesBSQIssued, seriesBSQBurnt;

    private static final Map<String, TemporalAdjuster> ADJUSTERS = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private SupplyView(DaoFacade daoFacade,
                       DaoStateService daoStateService,
                       BsqFormatter bsqFormatter) {
        this.daoFacade = daoFacade;
        this.daoStateService = daoStateService;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {

        ADJUSTERS.put(MONTH, TemporalAdjusters.firstDayOfMonth());
        ADJUSTERS.put(DAY, TemporalAdjusters.ofDateAdjuster(d -> d));

        createSupplyIncreasedInformation();
        createSupplyReducedInformation();
        createSupplyLockedInformation();
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

    private void createSupplyIncreasedInformation() {
        addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.factsAndFigures.supply.issued"));

        Tuple3<Label, TextField, VBox> genesisAmountTuple = addTopLabelReadOnlyTextField(root, gridRow,
                Res.get("dao.factsAndFigures.supply.genesisIssueAmount"), Layout.FIRST_ROW_DISTANCE);
        genesisIssueAmountTextField = genesisAmountTuple.second;
        GridPane.setColumnSpan(genesisAmountTuple.third, 2);

        compRequestIssueAmountTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.supply.compRequestIssueAmount")).second;
        reimbursementAmountTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.supply.reimbursementAmount")).second;


        seriesBSQIssued = new XYChart.Series<>();
        createChart(seriesBSQIssued, Res.get("dao.factsAndFigures.supply.issued"), "MMM uu");
    }

    private void createSupplyReducedInformation() {
        addTitledGroupBg(root, ++gridRow, 2, Res.get("dao.factsAndFigures.supply.burnt"), Layout.GROUP_DISTANCE);

        totalBurntFeeAmountTextField = addTopLabelReadOnlyTextField(root, gridRow,
                Res.get("dao.factsAndFigures.supply.burntAmount"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        totalAmountOfInvalidatedBsqTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.supply.invalidTxs"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;

        seriesBSQBurnt = new XYChart.Series<>();
        createChart(seriesBSQBurnt, Res.get("dao.factsAndFigures.supply.burnt"), "d MMM");
    }

    private void createSupplyLockedInformation() {
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

    private void createChart(XYChart.Series<Number, Number> series, String seriesLabel, String datePattern) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(true);
        xAxis.setTickLabelGap(6);
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);
        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number timestamp) {
                LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(timestamp.longValue(),
                        0, OffsetDateTime.now(ZoneId.systemDefault()).getOffset());
                return localDateTime.format(DateTimeFormatter.ofPattern(datePattern, GlobalSettings.getLocale()));
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setSide(Side.RIGHT);
        yAxis.setAutoRanging(true);
        yAxis.setTickMarkVisible(false);
        yAxis.setMinorTickVisible(false);
        yAxis.setTickLabelGap(5);
        yAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number marketPrice) {
                return bsqFormatter.formatBSQSatoshisWithCode(marketPrice.longValue());
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        series.setName(seriesLabel);

        AreaChart<Number, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setId("charts-dao");
        chart.setMinHeight(250);
        chart.setPrefHeight(250);
        chart.setCreateSymbols(true);
        chart.setPadding(new Insets(0));
        chart.getData().add(series);

        AnchorPane chartPane = new AnchorPane();
        chartPane.getStyleClass().add("chart-pane");

        AnchorPane.setTopAnchor(chart, 15d);
        AnchorPane.setBottomAnchor(chart, 10d);
        AnchorPane.setLeftAnchor(chart, 25d);
        AnchorPane.setRightAnchor(chart, 10d);

        chartPane.getChildren().add(chart);

        GridPane.setColumnSpan(chartPane, 2);
        GridPane.setRowIndex(chartPane, ++gridRow);
        GridPane.setMargin(chartPane, new Insets(10, 0, 0, 0));

        root.getChildren().add(chartPane);
    }

    private void updateWithBsqBlockChainData() {
        Coin issuedAmountFromGenesis = daoFacade.getGenesisTotalSupply();
        genesisIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromGenesis));

        Coin issuedAmountFromCompRequests = Coin.valueOf(daoFacade.getTotalIssuedAmount(IssuanceType.COMPENSATION));
        compRequestIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromCompRequests));
        Coin issuedAmountFromReimbursementRequests = Coin.valueOf(daoFacade.getTotalIssuedAmount(IssuanceType.REIMBURSEMENT));
        reimbursementAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromReimbursementRequests));

        Coin totalBurntFee = Coin.valueOf(daoFacade.getTotalBurntFee());
        Coin totalLockedUpAmount = Coin.valueOf(daoFacade.getTotalLockupAmount());
        Coin totalUnlockingAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockingTxOutputs());
        Coin totalUnlockedAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockedTxOutputs());
        Coin totalConfiscatedAmount = Coin.valueOf(daoFacade.getTotalAmountOfConfiscatedTxOutputs());
        Coin totalAmountOfInvalidatedBsq = Coin.valueOf(daoFacade.getTotalAmountOfInvalidatedBsq());

        totalBurntFeeAmountTextField.setText("-" + bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalBurntFee));
        totalLockedUpAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalLockedUpAmount));
        totalUnlockingAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockingAmount));
        totalUnlockedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockedAmount));
        totalConfiscatedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalConfiscatedAmount));
        String minusSign = totalAmountOfInvalidatedBsq.isPositive() ? "-" : "";
        totalAmountOfInvalidatedBsqTextField.setText(minusSign + bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalAmountOfInvalidatedBsq));

        updateCharts();
    }

    private void updateCharts() {
        seriesBSQIssued.getData().clear();
        seriesBSQBurnt.getData().clear();

        Set<Tx> burntTxs = new HashSet<>(daoStateService.getBurntFeeTxs());
        burntTxs.addAll(daoStateService.getInvalidTxs());

        Map<LocalDate, List<Tx>> burntBsqByDay = burntTxs.stream()
                .sorted(Comparator.comparing(Tx::getTime))
                .collect(Collectors.groupingBy(item -> new Date(item.getTime()).toLocalDate()
                        .with(ADJUSTERS.get(DAY))));

        List<XYChart.Data<Number, Number>> updatedBurntBsq = burntBsqByDay.keySet().stream()
                .map(date -> {
                    ZonedDateTime zonedDateTime = date.atStartOfDay(ZoneId.systemDefault());
                    return new XYChart.Data<Number, Number>(zonedDateTime.toInstant().getEpochSecond(), burntBsqByDay.get(date)
                            .stream()
                            .mapToDouble(Tx::getBurntBsq)
                            .sum()
                    );
                })
                .collect(Collectors.toList());

        seriesBSQBurnt.getData().setAll(updatedBurntBsq);

        Stream<Issuance> bsqByCompensation = daoStateService.getIssuanceSet(IssuanceType.COMPENSATION).stream()
                .sorted(Comparator.comparing(Issuance::getChainHeight));

        Stream<Issuance> bsqByReimbursement = daoStateService.getIssuanceSet(IssuanceType.REIMBURSEMENT).stream()
                .sorted(Comparator.comparing(Issuance::getChainHeight));

        Map<LocalDate, List<Issuance>> bsqAddedByVote = Stream.concat(bsqByCompensation, bsqByReimbursement)
                .collect(Collectors.groupingBy(item -> new Date(daoFacade.getBlockTime(item.getChainHeight())).toLocalDate()
                        .with(ADJUSTERS.get(MONTH))));

        List<XYChart.Data<Number, Number>> updatedAddedBSQ = bsqAddedByVote.keySet().stream()
                .map(date -> {
                    ZonedDateTime zonedDateTime = date.atStartOfDay(ZoneId.systemDefault());
                    return new XYChart.Data<Number, Number>(zonedDateTime.toInstant().getEpochSecond(), bsqAddedByVote.get(date)
                            .stream()
                            .mapToDouble(Issuance::getAmount)
                            .sum());
                })
                .collect(Collectors.toList());

        seriesBSQIssued.getData().setAll(updatedAddedBSQ);
    }
}

