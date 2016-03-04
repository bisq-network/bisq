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

package io.bitsquare.gui.main.markets.charts;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.offer.BuyOfferView;
import io.bitsquare.gui.main.offer.SellOfferView;
import io.bitsquare.gui.main.offer.offerbook.OfferBookListItem;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.inject.Inject;

@FxmlView
public class MarketsChartsView extends ActivatableViewAndModel<VBox, MarketsChartsViewModel> {

    private NumberAxis xAxis, yAxis;
    XYChart.Series seriesBuy, seriesSell;
    private final ListChangeListener<OfferBookListItem> changeListener;
    private final Navigation navigation;
    private final BSFormatter formatter;
    private TableView<Offer> buyOfferTableView;
    private TableView<Offer> sellOfferTableView;
    private AreaChart<Number, Number> areaChart;
    private ComboBox<TradeCurrency> currencyComboBox;
    private Subscription tradeCurrencySubscriber;
    private final StringProperty priceColumnLabel = new SimpleStringProperty();
    private final StringProperty volumeColumnLabel = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MarketsChartsView(MarketsChartsViewModel model, Navigation navigation, BSFormatter formatter) {
        super(model);
        this.navigation = navigation;
        this.formatter = formatter;

        changeListener = c -> updateChartData();
    }

    @Override
    public void initialize() {
        currencyComboBox = new ComboBox<>();
        currencyComboBox.setPromptText("Select currency");
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });


        Label currencyLabel = new Label("Currency:");
        HBox currencyHBox = new HBox();
        currencyHBox.setSpacing(5);
        currencyHBox.setPadding(new Insets(10, -20, 0, 20));
        currencyHBox.setAlignment(Pos.CENTER_LEFT);
        currencyHBox.getChildren().addAll(currencyLabel, currencyComboBox);

        createChart();

        Tuple3<TableView<Offer>, VBox, Button> tupleBuy = getOfferTable(Offer.Direction.BUY);
        Tuple3<TableView<Offer>, VBox, Button> tupleSell = getOfferTable(Offer.Direction.SELL);
        buyOfferTableView = tupleBuy.first;
        sellOfferTableView = tupleSell.first;

        HBox hBox = new HBox();
        hBox.setSpacing(30);
        hBox.setAlignment(Pos.CENTER);
        hBox.getChildren().addAll(tupleBuy.second, tupleSell.second);

        root.getChildren().addAll(currencyHBox, areaChart, hBox);
    }

    @Override
    protected void activate() {
        currencyComboBox.setItems(model.getTradeCurrencies());
        currencyComboBox.getSelectionModel().select(model.getTradeCurrency());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 25));
        currencyComboBox.setOnAction(e -> {
            model.onSetTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
            updateChartData();
        });

        model.getOfferBookListItems().addListener(changeListener);
        tradeCurrencySubscriber = EasyBind.subscribe(model.tradeCurrency,
                newValue -> {
                    String code = newValue.getCode();
                    areaChart.setTitle("Offer book for " + newValue.getName());
                    priceColumnLabel.set("Price (" + code + "/BTC)");
                    volumeColumnLabel.set("Volume (" + code + ")");
                    xAxis.setLabel(priceColumnLabel.get());
                    xAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(xAxis, "", ""));
                });

        buyOfferTableView.setItems(model.getBuyOfferList());
        sellOfferTableView.setItems(model.getSellOfferList());

        updateChartData();
    }

    @Override
    protected void deactivate() {
        model.getOfferBookListItems().removeListener(changeListener);
        tradeCurrencySubscriber.unsubscribe();
        currencyComboBox.setOnAction(null);
    }


    private Tuple3<TableView<Offer>, VBox, Button> getOfferTable(Offer.Direction direction) {
        TableView<Offer> tableView = new TableView<>();
        tableView.setMinHeight(100);
        tableView.setMinWidth(390);

        // price
        TableColumn<Offer, Offer> priceColumn = new TableColumn<>();
        priceColumn.textProperty().bind(priceColumnLabel);
        priceColumn.setMinWidth(120);
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer, Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            @Override
                            public void updateItem(final Offer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatFiat(item.getPrice()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(priceColumn);

        // amount
        TableColumn<Offer, Offer> amountColumn = new TableColumn<>("Amount (BTC)");
        amountColumn.setText("Amount (BTC)");
        amountColumn.setMinWidth(120);
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer, Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            @Override
                            public void updateItem(final Offer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatCoin(item.getAmount()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(amountColumn);

        // volume
        TableColumn<Offer, Offer> volumeColumn = new TableColumn<>("Amount (BTC)");
        volumeColumn.setMinWidth(120);
        volumeColumn.textProperty().bind(volumeColumnLabel);
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<Offer, Offer>, TableCell<Offer, Offer>>() {
                    @Override
                    public TableCell<Offer, Offer> call(TableColumn<Offer, Offer> column) {
                        return new TableCell<Offer, Offer>() {
                            @Override
                            public void updateItem(final Offer item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(formatter.formatFiat(item.getOfferVolume()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        tableView.getColumns().add(volumeColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("Currently there are no offers available");
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);
        tableView.getSelectionModel().setCellSelectionEnabled(false);

        Label titleLabel = new Label(direction.equals(Offer.Direction.BUY) ? "Offers for buying bitcoin  (bid)" : "Offers for selling bitcoin  (ask)");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16; -fx-alignment: center");
        UserThread.execute(() -> titleLabel.prefWidthProperty().bind(tableView.widthProperty()));

        boolean isSellOffer = direction == Offer.Direction.SELL;
        Button button = new Button();
        ImageView iconView = new ImageView();
        iconView.setId(isSellOffer ? "image-buy-white" : "image-sell-white");
        button.setGraphic(iconView);
        button.setGraphicTextGap(10);
        button.setText(isSellOffer ? "I want to buy bitcoin" : "I want to sell bitcoin");
        button.setMinHeight(40);
        button.setId(isSellOffer ? "buy-button-big" : "sell-button-big");
        button.setOnAction(e -> navigation.navigateTo(MainView.class, isSellOffer ? BuyOfferView.class : SellOfferView.class));

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.setFillWidth(true);
        vBox.setMinHeight(120);
        vBox.getChildren().addAll(titleLabel, tableView, button);

        button.prefWidthProperty().bind(vBox.widthProperty());
        return new Tuple3<>(tableView, vBox, button);
    }


    private void createChart() {
        xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setAutoRanging(true);
        xAxis.setLabel(priceColumnLabel.get());

        yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(true);
        yAxis.setLabel("Amount in BTC");
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis, "", ""));

        seriesBuy = new XYChart.Series();
        seriesBuy.setName("Offers for buying bitcoin   ");

        seriesSell = new XYChart.Series();
        seriesSell.setName("Offers for selling bitcoin ");

        areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setAnimated(false);
        areaChart.setId("charts");
        areaChart.setMinHeight(300);
        areaChart.setPadding(new Insets(0, 30, 10, 0));
        areaChart.getData().addAll(seriesBuy, seriesSell);
    }


    private void updateChartData() {
        seriesBuy.getData().clear();
        seriesSell.getData().clear();

        seriesBuy.getData().addAll(model.getBuyData());
        seriesSell.getData().addAll(model.getSellData());
    }
}
