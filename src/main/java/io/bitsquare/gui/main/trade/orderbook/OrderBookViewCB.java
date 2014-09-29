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

package io.bitsquare.gui.main.trade.orderbook;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.trade.TradeNavigator;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.validation.OptionalBtcValidator;
import io.bitsquare.gui.util.validation.OptionalFiatValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.Country;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.beans.binding.Bindings.createStringBinding;

/**
 * TODO: The advanced filters are not impl. yet
 * The restrictions handling is open from the concept and is only implemented for countries yet.
 */
public class OrderBookViewCB extends CachedViewCB<OrderBookPM> {
    private static final Logger log = LoggerFactory.getLogger(OrderBookViewCB.class);

    private final Navigation navigation;
    private final OverlayManager overlayManager;
    private final OptionalBtcValidator optionalBtcValidator;
    private final OptionalFiatValidator optionalFiatValidator;

    private boolean detailsVisible;
    private boolean advancedScreenInited;

    private ImageView expand;
    private ImageView collapse;

    @FXML CheckBox showOnlyMatchingCheckBox;
    @FXML Label amountBtcLabel, priceDescriptionLabel, priceFiatLabel, volumeDescriptionLabel,
            volumeFiatLabel, extendedButton1Label, extendedButton2Label, extendedCheckBoxLabel;
    @FXML InputTextField volumeTextField, amountTextField, priceTextField;
    @FXML TableView<OrderBookListItem> table;
    @FXML Button createOfferButton, showAdvancedSettingsButton, openCountryFilterButton, openPaymentMethodsFilterButton;
    @FXML TableColumn<OrderBookListItem, OrderBookListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, countryColumn, bankAccountTypeColumn;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OrderBookViewCB(OrderBookPM presentationModel,
                    Navigation navigation,
                    OverlayManager overlayManager,
                    OptionalBtcValidator optionalBtcValidator,
                    OptionalFiatValidator optionalFiatValidator) {
        super(presentationModel);

        this.navigation = navigation;
        this.overlayManager = overlayManager;
        this.optionalBtcValidator = optionalBtcValidator;
        this.optionalFiatValidator = optionalFiatValidator;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // init table
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setCountryColumnCellFactory();
        setBankAccountTypeColumnCellFactory();
        setDirectionColumnCellFactory();

        table.getSortOrder().add(priceColumn);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("No offers available.\nTry to change your filter or account settings.");
        placeholder.setWrapText(true);
        table.setPlaceholder(placeholder);

        setupBindings();
        setupValidators();
        setupComparators();

        expand = ImageUtil.getImageViewById(ImageUtil.EXPAND);
        collapse = ImageUtil.getImageViewById(ImageUtil.COLLAPSE);
        showAdvancedSettingsButton.setGraphic(expand);

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        // setOrderBookInfo has been called before
        SortedList<OrderBookListItem> offerList = presentationModel.getOfferList();
        table.setItems(offerList);
        offerList.comparatorProperty().bind(table.comparatorProperty());
        priceColumn.setSortType((presentationModel.getDirection() == Direction.BUY) ?
                TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);
        table.sort();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();

    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void enableCreateOfferButton() {
        createOfferButton.setDisable(false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setDirection(Direction direction) {
        presentationModel.setDirection(direction);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    void createOffer() {
        if (presentationModel.isRegistered()) {
            if (presentationModel.getDirection() == Direction.BUY) {
                createOfferButton.setDisable(true);
                ((TradeNavigator) parent).createOffer(presentationModel.getAmountAsCoin(),
                        presentationModel.getPriceAsCoin());
            }
            else {
                Popups.openWarningPopup("Under construction", "At the moment only the creation of buy offers is " +
                        "implemented.");
            }
        }
        else {
            openSetupScreen();
        }
    }

    @FXML
    void onToggleShowAdvancedSettings() {
        detailsVisible = !detailsVisible;
        if (detailsVisible) {
            showAdvancedSettingsButton.setText(BSResources.get("Hide extended filter options"));
            showAdvancedSettingsButton.setGraphic(collapse);
            showDetailsScreen();
        }
        else {
            showAdvancedSettingsButton.setText(BSResources.get("Show more filter options"));
            showAdvancedSettingsButton.setGraphic(expand);
            hideDetailsScreen();
        }
    }

    @FXML
    void onShowOnlyMatching() {
        Popups.openWarningPopup("Under construction", "This feature is not implemented yet.");
    }

    @FXML
    void onOpenCountryFilter() {
        Popups.openWarningPopup("Under construction", "This feature is not implemented yet.");
    }

    @FXML
    void onOpenPaymentMethodsFilter() {
        Popups.openWarningPopup("Under construction", "This feature is not implemented yet.");
    }
    

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void openSetupScreen() {
        overlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.ok")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                Dialog.Actions.OK.handle(actionEvent);
                overlayManager.removeBlurContent();
                navigation.setItemsForReturning(navigation.getCurrentItems());
                navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                        Navigation.Item.ACCOUNT_SETUP);
            }
        });
        Popups.openInfo("You need to setup your trading account before you can trade.",
                "You don't have a trading account.", actions);
    }

    private void takeOffer(Offer offer) {
        if (presentationModel.isRegistered()) {
            ((TradeNavigator) parent).takeOffer(presentationModel.getAmountAsCoin(),
                    presentationModel.getPriceAsCoin(), offer);
        }
        else {
            openSetupScreen();
        }
    }

    private void openRestrictionsWarning(String restrictionsInfo) {
        List<Action> actions = new ArrayList<>();
        actions.add(Dialog.Actions.YES);
        actions.add(Dialog.Actions.CLOSE);

        Action response = Popups.openConfirmPopup("Information",
                restrictionsInfo,
                "You do not fulfill the requirements of that offer.",
                actions);

        if (response == Dialog.Actions.YES)
            navigation.navigationTo(Navigation.Item.MAIN, Navigation.Item.ACCOUNT,
                    Navigation.Item.ACCOUNT_SETTINGS,
                    Navigation.Item.RESTRICTIONS);
        else
            table.getSelectionModel().clearSelection();
    }

    private void showDetailsScreen() {
        if (!advancedScreenInited) {
            advancedScreenInited = true;
        }

        toggleDetailsScreen(true);
    }

    private void hideDetailsScreen() {
        toggleDetailsScreen(false);
    }

    private void toggleDetailsScreen(boolean visible) {
        ((GridPane) root).setVgap(visible ? 5 : 0);

        extendedButton1Label.setVisible(visible);
        extendedButton1Label.setManaged(visible);

        extendedButton2Label.setVisible(visible);
        extendedButton2Label.setManaged(visible);

        extendedCheckBoxLabel.setVisible(visible);
        extendedCheckBoxLabel.setManaged(visible);

        openCountryFilterButton.setVisible(visible);
        openCountryFilterButton.setManaged(visible);

        openPaymentMethodsFilterButton.setVisible(visible);
        openPaymentMethodsFilterButton.setManaged(visible);

        showOnlyMatchingCheckBox.setVisible(visible);
        showOnlyMatchingCheckBox.setManaged(visible);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setup
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupBindings() {
        amountTextField.textProperty().bindBidirectional(presentationModel.amount);
        priceTextField.textProperty().bindBidirectional(presentationModel.price);
        volumeTextField.textProperty().bindBidirectional(presentationModel.volume);
        amountBtcLabel.textProperty().bind(presentationModel.btcCode);
        priceFiatLabel.textProperty().bind(presentationModel.fiatCode);
        volumeFiatLabel.textProperty().bind(presentationModel.fiatCode);
        priceDescriptionLabel.textProperty().bind(presentationModel.fiatCode);
        volumeDescriptionLabel.textProperty().bind(presentationModel.fiatCode);//Price per Bitcoin in EUR
        priceDescriptionLabel.textProperty().bind(createStringBinding(() ->
                        BSResources.get("Filter by price in {0}", presentationModel.fiatCode.get()),
                presentationModel.fiatCode));
        volumeDescriptionLabel.textProperty().bind(createStringBinding(() ->
                        BSResources.get("Filter by amount in {0}", presentationModel.fiatCode.get()),
                presentationModel.fiatCode));
        volumeTextField.promptTextProperty().bind(createStringBinding(() ->
                        BSResources.get("Amount in {0}", presentationModel.fiatCode.get()),
                presentationModel.fiatCode));
    }

    private void setupValidators() {
        amountTextField.setValidator(optionalBtcValidator);
        priceTextField.setValidator(optionalFiatValidator);
        volumeTextField.setValidator(optionalFiatValidator);
    }

    private void setupComparators() {
        priceColumn.setComparator((o1, o2) -> o1.getOffer().getPrice().compareTo(o2.getOffer().getPrice()));
        amountColumn.setComparator((o1, o2) -> o1.getOffer().getAmount().compareTo(o2.getOffer().getAmount()));
        volumeColumn.setComparator((o1, o2) ->
                o1.getOffer().getOfferVolume().compareTo(o2.getOffer().getOfferVolume()));
        countryColumn.setComparator((o1, o2) -> o1.getOffer().getBankAccountCountry().getName().compareTo(o2.getOffer()
                .getBankAccountCountry().getName()));
        bankAccountTypeColumn.setComparator((o1, o2) -> o1.getOffer().getBankAccountType().compareTo(o2.getOffer()
                .getBankAccountType()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem,
                        OrderBookListItem>>() {
                    @Override
                    public TableCell<OrderBookListItem, OrderBookListItem> call(
                            TableColumn<OrderBookListItem, OrderBookListItem> column) {
                        return new TableCell<OrderBookListItem, OrderBookListItem>() {
                            @Override
                            public void updateItem(final OrderBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getAmount(item));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem,
                        OrderBookListItem>>() {
                    @Override
                    public TableCell<OrderBookListItem, OrderBookListItem> call(
                            TableColumn<OrderBookListItem, OrderBookListItem> column) {
                        return new TableCell<OrderBookListItem, OrderBookListItem>() {
                            @Override
                            public void updateItem(final OrderBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getPrice(item));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem,
                        OrderBookListItem>>() {
                    @Override
                    public TableCell<OrderBookListItem, OrderBookListItem> call(
                            TableColumn<OrderBookListItem, OrderBookListItem> column) {
                        return new TableCell<OrderBookListItem, OrderBookListItem>() {
                            @Override
                            public void updateItem(final OrderBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getVolume(item));
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem,
                        OrderBookListItem>>() {

                    @Override
                    public TableCell<OrderBookListItem, OrderBookListItem> call(
                            TableColumn<OrderBookListItem, OrderBookListItem> column) {
                        return new TableCell<OrderBookListItem, OrderBookListItem>() {
                            final ImageView iconView = new ImageView();
                            final Button button = new Button();

                            {
                                button.setGraphic(iconView);
                                button.setMinWidth(70);
                            }

                            private void verifyIfTradable(final OrderBookListItem item) {
                                boolean isMatchingRestrictions = presentationModel.isTradable(item
                                        .getOffer());
                                button.setDisable(!isMatchingRestrictions);

                                TableRow tableRow = getTableRow();
                                if (tableRow != null)
                                    tableRow.setOpacity(isMatchingRestrictions ? 1 : 0.4);

                                if (isMatchingRestrictions) {
                                    button.setDefaultButton(getIndex() == 0);
                                    if (tableRow != null) {
                                        getTableRow().setOnMouseClicked(null);
                                        getTableRow().setTooltip(null);
                                    }
                                }
                                else {

                                    button.setDefaultButton(false);
                                    if (tableRow != null) {
                                        getTableRow().setTooltip(new Tooltip("Click for more information."));
                                        getTableRow().setOnMouseClicked((e) -> openRestrictionsWarning
                                                (presentationModel.restrictionsInfo.get()));
                                    }
                                }
                            }

                            @Override
                            public void updateItem(final OrderBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null) {
                                    String title;
                                    Offer offer = item.getOffer();

                                    if (presentationModel.isMyOffer(offer)) {
                                        iconView.setId("image-remove");
                                        title = "Remove";
                                        button.setOnAction(event -> presentationModel.removeOffer(item
                                                .getOffer()));
                                    }
                                    else {
                                        if (offer.getDirection() == Direction.SELL)
                                            iconView.setId("image-buy");
                                        else
                                            iconView.setId("image-sell");
                                        title = presentationModel.getDirectionLabel(offer);
                                        button.setOnAction(event -> takeOffer(item.getOffer()));
                                    }

                                    item.bankAccountCountryProperty().addListener((ov, o, n) ->
                                            verifyIfTradable(item));
                                    verifyIfTradable(item);

                                    button.setText(title);
                                    setGraphic(button);
                                }
                                else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setCountryColumnCellFactory() {
        countryColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        countryColumn.setCellFactory(
                new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem,
                        OrderBookListItem>>() {

                    @Override
                    public TableCell<OrderBookListItem, OrderBookListItem> call(
                            TableColumn<OrderBookListItem, OrderBookListItem> column) {
                        return new TableCell<OrderBookListItem, OrderBookListItem>() {
                            final HBox hBox = new HBox();

                            {
                                hBox.setSpacing(3);
                                hBox.setAlignment(Pos.CENTER);
                                setGraphic(hBox);
                            }

                            @Override
                            public void updateItem(final OrderBookListItem orderBookListItem, boolean empty) {
                                super.updateItem(orderBookListItem, empty);

                                hBox.getChildren().clear();
                                if (orderBookListItem != null) {
                                    Country country = orderBookListItem.getOffer().getBankAccountCountry();
                                    hBox.getChildren().add(ImageUtil.getCountryIconImageView(orderBookListItem
                                            .getOffer().getBankAccountCountry()));
                                    Tooltip.install(this, new Tooltip(country.getName()));
                                }
                            }
                        };
                    }
                });
    }

    private void setBankAccountTypeColumnCellFactory() {
        bankAccountTypeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        bankAccountTypeColumn.setCellFactory(
                new Callback<TableColumn<OrderBookListItem, OrderBookListItem>, TableCell<OrderBookListItem,
                        OrderBookListItem>>() {
                    @Override
                    public TableCell<OrderBookListItem, OrderBookListItem> call(
                            TableColumn<OrderBookListItem, OrderBookListItem> column) {
                        return new TableCell<OrderBookListItem, OrderBookListItem>() {
                            @Override
                            public void updateItem(final OrderBookListItem orderBookListItem, boolean empty) {
                                super.updateItem(orderBookListItem, empty);
                                setText(presentationModel.getBankAccountType(orderBookListItem));
                            }
                        };
                    }
                });
    }
}

