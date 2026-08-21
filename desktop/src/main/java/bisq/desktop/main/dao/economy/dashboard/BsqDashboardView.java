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

package bisq.desktop.main.dao.economy.dashboard;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.TextFieldWithIcon;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.economy.dashboard.price.PriceChartView;
import bisq.desktop.main.dao.economy.dashboard.volume.VolumeChartView;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.AveragePriceUtil;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;

import javafx.collections.ObservableList;

import java.text.DecimalFormat;

import java.util.Optional;

import static bisq.desktop.util.FormBuilder.addLabelWithSubText;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static bisq.desktop.util.FormBuilder.addTopLabelReadOnlyTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelTextFieldWithIcon;


@FxmlView
public class BsqDashboardView extends ActivatableView<GridPane, Void> implements DaoStateListener {

    private final PriceChartView priceChartView;
    private final VolumeChartView volumeChartView;
    private final DaoFacade daoFacade;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;

    private TextField avgPrice90TextField, avgUSDPrice90TextField, marketCapTextField, availableAmountTextField,
            usdVolumeTextField, btcVolumeTextField, averageBsqUsdPriceTextField, averageBsqBtcPriceTextField;
    private TextFieldWithIcon avgPrice30TextField, avgUSDPrice30TextField;
    private Label marketPriceLabel;

    private ChangeListener<Number> priceChangeListener;
    private int gridRow = 0;
    private Coin availableAmount;
    private Price avg30DayUSDPrice;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqDashboardView(PriceChartView priceChartView,
                            VolumeChartView volumeChartView,
                            DaoFacade daoFacade,
                            TradeStatisticsManager tradeStatisticsManager,
                            PriceFeedService priceFeedService,
                            Preferences preferences,
                            BsqFormatter bsqFormatter) {
        this.priceChartView = priceChartView;
        this.volumeChartView = volumeChartView;
        this.daoFacade = daoFacade;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        createTextFields();
        createPriceChart();
        createTradeChart();

        priceChangeListener = (observable, oldValue, newValue) -> {
            updatePrice();
            updateAveragePriceFields(avgPrice90TextField, avgPrice30TextField, false);
            updateAveragePriceFields(avgUSDPrice90TextField, avgUSDPrice30TextField, true);
            updateMarketCap();
        };
    }

    @Override
    protected void activate() {
        daoFacade.addBsqStateListener(this);
        priceFeedService.updateCounterProperty().addListener(priceChangeListener);

        updateWithBsqBlockChainData();
        updatePrice();
        updateAveragePriceFields(avgPrice90TextField, avgPrice30TextField, false);
        updateAveragePriceFields(avgUSDPrice90TextField, avgUSDPrice30TextField, true);
        updateMarketCap();

        averageBsqUsdPriceTextField.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    DecimalFormat priceFormat = (DecimalFormat) DecimalFormat.getNumberInstance(GlobalSettings.getLocale());
                    priceFormat.setMaximumFractionDigits(4);
                    return priceFormat.format(priceChartView.averageBsqUsdPriceProperty().get()) + " BSQ/USD";
                },
                priceChartView.averageBsqUsdPriceProperty()));
        averageBsqBtcPriceTextField.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    DecimalFormat priceFormat = (DecimalFormat) DecimalFormat.getNumberInstance(GlobalSettings.getLocale());
                    priceFormat.setMaximumFractionDigits(8);
                  /*  yAxisFormatter = value -> {
                        value = MathUtils.scaleDownByPowerOf10(value.longValue(), 8);
                        return priceFormat.format(value) + " BSQ/BTC";
                    };*/

                    double scaled = MathUtils.scaleDownByPowerOf10(priceChartView.averageBsqBtcPriceProperty().get(), 8);
                    return priceFormat.format(scaled) + " BSQ/BTC";
                },
                priceChartView.averageBsqBtcPriceProperty()));

        usdVolumeTextField.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    DecimalFormat volumeFormat = (DecimalFormat) DecimalFormat.getNumberInstance(GlobalSettings.getLocale());
                    volumeFormat.setMaximumFractionDigits(0);
                    double scaled = MathUtils.scaleDownByPowerOf10(volumeChartView.usdVolumeProperty().get(), 4);
                    return volumeFormat.format(scaled) + " USD";
                },
                volumeChartView.usdVolumeProperty()));
        btcVolumeTextField.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    DecimalFormat volumeFormat = (DecimalFormat) DecimalFormat.getNumberInstance(GlobalSettings.getLocale());
                    volumeFormat.setMaximumFractionDigits(4);
                    double scaled = MathUtils.scaleDownByPowerOf10(volumeChartView.btcVolumeProperty().get(), 8);
                    return volumeFormat.format(scaled) + " BTC";
                },
                volumeChartView.btcVolumeProperty()));
    }


    @Override
    protected void deactivate() {
        daoFacade.removeBsqStateListener(this);
        priceFeedService.updateCounterProperty().removeListener(priceChangeListener);

        averageBsqUsdPriceTextField.textProperty().unbind();
        averageBsqBtcPriceTextField.textProperty().unbind();
        usdVolumeTextField.textProperty().unbind();
        btcVolumeTextField.textProperty().unbind();
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

    private void createTextFields() {
        Tuple3<Label, Label, VBox> marketPriceBox = addLabelWithSubText(root, gridRow++, "", "");
        marketPriceLabel = marketPriceBox.first;
        marketPriceLabel.getStyleClass().add("dao-kpi-big");

        marketPriceBox.second.getStyleClass().add("dao-kpi-subtext");

        avgUSDPrice90TextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.dashboard.avgUSDPrice90"), -20).second;

        avgUSDPrice30TextField = addTopLabelTextFieldWithIcon(root, gridRow, 1,
                Res.get("dao.factsAndFigures.dashboard.avgUSDPrice30"), -35).second;
        AnchorPane.setRightAnchor(avgUSDPrice30TextField.getIconLabel(), 10d);

        avgPrice90TextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.dashboard.avgPrice90")).second;

        avgPrice30TextField = addTopLabelTextFieldWithIcon(root, gridRow, 1,
                Res.get("dao.factsAndFigures.dashboard.avgPrice30"), -15).second;
        AnchorPane.setRightAnchor(avgPrice30TextField.getIconLabel(), 10d);

        marketCapTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.dashboard.marketCap")).second;

        availableAmountTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.dashboard.availableAmount")).second;
    }

    private void createPriceChart() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 2,
                Res.get("dao.factsAndFigures.supply.priceChat"), Layout.FLOATING_LABEL_DISTANCE);
        titledGroupBg.getStyleClass().add("last");

        priceChartView.initialize();
        VBox chartContainer = priceChartView.getRoot();

        AnchorPane chartPane = new AnchorPane();
        chartPane.getStyleClass().add("chart-pane");
        AnchorPane.setTopAnchor(chartContainer, 15d);
        AnchorPane.setBottomAnchor(chartContainer, 0d);
        AnchorPane.setLeftAnchor(chartContainer, 25d);
        AnchorPane.setRightAnchor(chartContainer, 10d);
        GridPane.setColumnSpan(chartPane, 2);
        GridPane.setRowIndex(chartPane, ++gridRow);
        GridPane.setMargin(chartPane, new Insets(Layout.COMPACT_FIRST_ROW_AND_COMPACT_GROUP_DISTANCE, 0, 0, 0));
        chartPane.getChildren().add(chartContainer);

        root.getChildren().add(chartPane);

        averageBsqUsdPriceTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.dashboard.averageBsqUsdPriceFromSelection")).second;
        averageBsqBtcPriceTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.dashboard.averageBsqBtcPriceFromSelection")).second;

    }

    private void createTradeChart() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 2,
                Res.get("dao.factsAndFigures.supply.volumeChat"), Layout.FLOATING_LABEL_DISTANCE);
        titledGroupBg.getStyleClass().add("last"); // hides separator as we add a second TitledGroupBg

        volumeChartView.initialize();
        VBox chartContainer = volumeChartView.getRoot();

        AnchorPane chartPane = new AnchorPane();
        chartPane.getStyleClass().add("chart-pane");
        AnchorPane.setTopAnchor(chartContainer, 15d);
        AnchorPane.setBottomAnchor(chartContainer, 0d);
        AnchorPane.setLeftAnchor(chartContainer, 25d);
        AnchorPane.setRightAnchor(chartContainer, 10d);
        GridPane.setColumnSpan(chartPane, 2);
        GridPane.setRowIndex(chartPane, ++gridRow);
        GridPane.setMargin(chartPane, new Insets(Layout.COMPACT_FIRST_ROW_AND_COMPACT_GROUP_DISTANCE, 0, 0, 0));
        chartPane.getChildren().add(chartContainer);

        root.getChildren().add(chartPane);

        usdVolumeTextField = addTopLabelReadOnlyTextField(root, ++gridRow,
                Res.get("dao.factsAndFigures.dashboard.volumeUsd")).second;
        btcVolumeTextField = addTopLabelReadOnlyTextField(root, gridRow, 1,
                Res.get("dao.factsAndFigures.dashboard.volumeBtc")).second;
    }

    private void updateWithBsqBlockChainData() {
        Coin issuedAmountFromGenesis = daoFacade.getGenesisTotalSupply();
        Coin issuedAmountFromCompRequests = Coin.valueOf(daoFacade.getTotalIssuedAmount(IssuanceType.COMPENSATION));
        Coin issuedAmountFromReimbursementRequests = Coin.valueOf(daoFacade.getTotalIssuedAmount(IssuanceType.REIMBURSEMENT));
        Coin totalConfiscatedAmount = Coin.valueOf(daoFacade.getTotalAmountOfConfiscatedTxOutputs());
        Coin totalAmountOfBurntBsq = Coin.valueOf(daoFacade.getTotalAmountOfBurntBsq());

        availableAmount = issuedAmountFromGenesis
                .add(issuedAmountFromCompRequests)
                .add(issuedAmountFromReimbursementRequests)
                .subtract(totalAmountOfBurntBsq)
                .subtract(totalConfiscatedAmount);

        availableAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(availableAmount));
    }

    private void updatePrice() {
        Optional<Price> optionalBsqPrice = priceFeedService.getBsqPrice();
        if (optionalBsqPrice.isPresent()) {
            Price bsqPrice = optionalBsqPrice.get();
            marketPriceLabel.setText(FormattingUtils.formatPrice(bsqPrice) + " BSQ/BTC");
        } else {
            marketPriceLabel.setText(Res.get("shared.na"));
        }
    }

    private void updateMarketCap() {
        if (avg30DayUSDPrice != null) {
            marketCapTextField.setText(bsqFormatter.formatMarketCap(avg30DayUSDPrice, availableAmount));
        } else {
            marketCapTextField.setText(Res.get("shared.na"));
        }
    }

    private void updateAveragePriceFields(TextField field90, TextFieldWithIcon field30, boolean isUSDField) {
        long average90 = updateAveragePriceField(field90, 90, isUSDField);
        long average30 = updateAveragePriceField(field30.getTextField(), 30, isUSDField);
        boolean trendUp = average30 > average90;
        boolean trendDown = average30 < average90;

        Label iconLabel = field30.getIconLabel();
        ObservableList<String> styleClass = iconLabel.getStyleClass();
        if (trendUp) {
            field30.setVisible(true);
            field30.setIcon(AwesomeIcon.CIRCLE_ARROW_UP);
            styleClass.remove("price-trend-down");
            styleClass.add("price-trend-up");
        } else if (trendDown) {
            field30.setVisible(true);
            field30.setIcon(AwesomeIcon.CIRCLE_ARROW_DOWN);
            styleClass.remove("price-trend-up");
            styleClass.add("price-trend-down");
        } else {
            iconLabel.setVisible(false);
        }
    }

    private long updateAveragePriceField(TextField textField, int days, boolean isUSDField) {
        Tuple2<Price, Price> tuple = AveragePriceUtil.getAveragePriceTuple(preferences, tradeStatisticsManager, days);
        Price usdPrice = tuple.first;
        Price bsqPrice = tuple.second;

        if (isUSDField) {
            textField.setText(usdPrice + " BSQ/USD");
            if (days == 30) {
                avg30DayUSDPrice = usdPrice;
            }
        } else {
            textField.setText(bsqPrice + " BSQ/BTC");
        }

        Price average = isUSDField ? usdPrice : bsqPrice;
        return average.getValue();
    }
}

