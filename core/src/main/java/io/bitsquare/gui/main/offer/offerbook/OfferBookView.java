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

package io.bitsquare.gui.main.offer.offerbook;

import io.bitsquare.common.viewfx.view.ActivatableViewAndModel;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.restrictions.RestrictionsView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import io.bitsquare.gui.main.account.setup.AccountSetupWizard;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.withdrawal.WithdrawalView;
import io.bitsquare.gui.main.offer.OfferView;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.validation.OptionalBtcValidator;
import io.bitsquare.gui.util.validation.OptionalFiatValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.Offer;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import static javafx.beans.binding.Bindings.createStringBinding;

// Note: countryColumn is deactivated in alpha version

@FxmlView
public class OfferBookView extends ActivatableViewAndModel<GridPane, OfferBookViewModel> {

    @FXML CheckBox showOnlyMatchingCheckBox;
    @FXML TableView<OfferBookListItem> table;
    @FXML InputTextField volumeTextField, amountTextField, priceTextField;
    @FXML Button createOfferButton, showAdvancedSettingsButton, openCountryFilterButton, openPaymentMethodsFilterButton;
    @FXML TableColumn<OfferBookListItem, OfferBookListItem> priceColumn, amountColumn, volumeColumn, directionColumn,
    /*countryColumn,*/ bankAccountTypeColumn;
    @FXML Label amountBtcLabel, priceDescriptionLabel, priceFiatLabel, volumeDescriptionLabel, volumeFiatLabel,
            extendedButton1Label, extendedButton2Label, extendedCheckBoxLabel;

    private ImageView expand;
    private ImageView collapse;

    private boolean detailsVisible;
    private boolean advancedScreenInited;


    private final Navigation navigation;
    private final OverlayManager overlayManager;
    private final OptionalBtcValidator optionalBtcValidator;
    private final OptionalFiatValidator optionalFiatValidator;
    private OfferView.OfferActionHandler offerActionHandler;

    @Inject
    OfferBookView(OfferBookViewModel model,
                  Navigation navigation,
                  OverlayManager overlayManager,
                  OptionalBtcValidator optionalBtcValidator,
                  OptionalFiatValidator optionalFiatValidator) {
        super(model);

        this.navigation = navigation;
        this.overlayManager = overlayManager;
        this.optionalBtcValidator = optionalBtcValidator;
        this.optionalFiatValidator = optionalFiatValidator;
    }

    @Override
    public void initialize() {
        // init table
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
      /*  setCountryColumnCellFactory();*/
        setBankAccountTypeColumnCellFactory();
        setDirectionColumnCellFactory();

        table.getSortOrder().add(priceColumn);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("Currently there are no offers available");
        placeholder.setWrapText(true);
        table.setPlaceholder(placeholder);


        setupValidators();
        setupComparators();

        expand = ImageUtil.getImageViewById(ImageUtil.EXPAND);
        collapse = ImageUtil.getImageViewById(ImageUtil.COLLAPSE);
        showAdvancedSettingsButton.setGraphic(expand);

        // for irc demo
        showAdvancedSettingsButton.setVisible(false);
        showAdvancedSettingsButton.setManaged(false);
    }

    @Override
    public void doActivate() {
        amountTextField.setText("");
        priceTextField.setText("");
        volumeTextField.setText("");

        setupBindings();

        // setOfferBookInfo has been called before
        SortedList<OfferBookListItem> offerList = model.getOfferList();
        table.setItems(offerList);
        offerList.comparatorProperty().bind(table.comparatorProperty());
        priceColumn.setSortType((model.getDirection() == Offer.Direction.BUY) ?
                TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);
        table.sort();

        //TODO temp for testing
        amountTextField.setText("1");
        priceTextField.setText("300");
        volumeTextField.setText("300");
    }

    @Override
    public void doDeactivate() {
        removeBindings();
    }

    public void enableCreateOfferButton() {
        createOfferButton.setDisable(false);
    }

    public void setDirection(Offer.Direction direction) {
        model.setDirection(direction);
    }

    @FXML
    void createOffer() {
        if (model.isRegistered()) {
            createOfferButton.setDisable(true);
            offerActionHandler.createOffer(model.getAmountAsCoin(), model.getPriceAsCoin());
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

    private void openSetupScreen() {
        overlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.ok")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "OK");
                Dialog.Actions.OK.handle(actionEvent);
                navigation.setReturnPath(navigation.getCurrentPath());
                navigation.navigateTo(MainView.class, AccountView.class, AccountSetupWizard.class);
            }
        });
        Popups.openInfoPopup("You don't have setup a trading account.",
                "You need to setup your trading account before you can trade.",
                actions);
    }

    private void takeOffer(Offer offer) {
        if (model.isRegistered()) {
            if (offer.getDirection() == Offer.Direction.BUY) {
                offerActionHandler.takeOffer(model.getAmountAsCoin(), model.getPriceAsCoin(), offer);
            }
            else {
                offerActionHandler.takeOffer(model.getAmountAsCoin(), model.getPriceAsCoin(), offer);
            }
        }
        else {
            openSetupScreen();
        }
    }

    private void onCancelOpenOffer(Offer offer) {
        model.onCancelOpenOffer(offer,
                () -> {
                    log.debug("Remove offer was successful");
                    Popups.openInfoPopup("You can withdraw the funds you paid in from the funds screens.");
                    navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
                },
                (message) -> {
                    log.error(message);
                    Popups.openWarningPopup("Remove offer failed", message);
                });

    }

    private void openRestrictionsWarning(String restrictionsInfo) {
        overlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.yes")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "YES");
                Dialog.Actions.YES.handle(actionEvent);
            }
        });
        actions.add(new AbstractAction(BSResources.get("shared.no")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "NO");
                Dialog.Actions.NO.handle(actionEvent);
            }
        });

        Action response = Popups.openConfirmPopup("Information",
                "You do not fulfill the requirements for that offer.",
                restrictionsInfo,
                actions);

        Popups.removeBlurContent();

        if (Popups.isYes(response))
            navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, RestrictionsView.class);
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
        root.setVgap(visible ? 5 : 0);

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

    private void setupBindings() {
        amountTextField.textProperty().bindBidirectional(model.amount);
        priceTextField.textProperty().bindBidirectional(model.price);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        amountBtcLabel.textProperty().bind(model.btcCode);
        priceFiatLabel.textProperty().bind(model.fiatCode);
        volumeFiatLabel.textProperty().bind(model.fiatCode);
        priceDescriptionLabel.textProperty().bind(model.fiatCode);
        volumeDescriptionLabel.textProperty().bind(model.fiatCode);//Price per Bitcoin in EUR
        priceDescriptionLabel.textProperty().bind(createStringBinding(() ->
                        BSResources.get("Filter by price in {0}", model.fiatCode.get()),
                model.fiatCode));
        volumeDescriptionLabel.textProperty().bind(createStringBinding(() ->
                        BSResources.get("Filter by amount in {0}", model.fiatCode.get()),
                model.fiatCode));
        volumeTextField.promptTextProperty().bind(createStringBinding(() ->
                        BSResources.get("Amount in {0}", model.fiatCode.get()),
                model.fiatCode));
    }

    private void removeBindings() {
        amountTextField.textProperty().unbind();
        priceTextField.textProperty().unbind();
        volumeTextField.textProperty().unbind();
        amountBtcLabel.textProperty().unbind();
        priceFiatLabel.textProperty().unbind();
        volumeFiatLabel.textProperty().unbind();
        priceDescriptionLabel.textProperty().unbind();
        volumeDescriptionLabel.textProperty().unbind();
        priceDescriptionLabel.textProperty().unbind();
        volumeDescriptionLabel.textProperty().unbind();
        volumeTextField.promptTextProperty().unbind();
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
      /*  countryColumn.setComparator((o1, o2) -> o1.getOffer().getBankAccountCountry().getName().compareTo(o2
      .getOffer()
                .getBankAccountCountry().getName()));*/
        bankAccountTypeColumn.setComparator((o1, o2) -> o1.getOffer().getFiatAccountType().compareTo(o2.getOffer()
                .getFiatAccountType()));
    }

    public void setOfferActionHandler(OfferView.OfferActionHandler offerActionHandler) {
        this.offerActionHandler = offerActionHandler;
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getAmount(item));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getPrice(item));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getVolume(item));
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {

                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            final ImageView iconView = new ImageView();
                            final Button button = new Button();

                            {
                                button.setGraphic(iconView);
                                button.setMinWidth(70);
                            }

                            private void verifyIfTradable(final OfferBookListItem item) {
                                boolean isMatchingRestrictions = model.isTradable(item
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
                                                (model.restrictionsInfo.get()));
                                    }
                                }
                            }

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null) {
                                    String title;
                                    Offer offer = item.getOffer();

                                    if (model.isMyOffer(offer)) {
                                        iconView.setId("image-remove");
                                        title = "Remove";
                                        button.setOnAction(event -> onCancelOpenOffer(item.getOffer()));
                                    }
                                    else {
                                        if (offer.getDirection() == Offer.Direction.SELL)
                                            iconView.setId("image-buy");
                                        else
                                            iconView.setId("image-sell");
                                        title = model.getDirectionLabel(offer);
                                        button.setOnAction(event -> takeOffer(item.getOffer()));
                                    }

                                    //TODO remove listener
                                    item.bankAccountCountryProperty().addListener((ov, o, n) -> verifyIfTradable(item));
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

   

   /* private void setCountryColumnCellFactory() {
        countryColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        countryColumn.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {

                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            final HBox hBox = new HBox();

                            {
                                hBox.setSpacing(3);
                                hBox.setAlignment(Pos.CENTER);
                                setGraphic(hBox);
                            }

                            @Override
                            public void updateItem(final OfferBookListItem offerBookListItem, boolean empty) {
                                super.updateItem(offerBookListItem, empty);

                                hBox.getChildren().clear();
                                if (offerBookListItem != null) {
                                    Country country = offerBookListItem.getOffer().getBankAccountCountry();
                                    hBox.getChildren().add(ImageUtil.getCountryIconImageView(offerBookListItem
                                            .getOffer().getBankAccountCountry()));
                                    Tooltip.install(this, new Tooltip(country.name));
                                }
                            }
                        };
                    }
                });
    }*/

    private void setBankAccountTypeColumnCellFactory() {
        bankAccountTypeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        bankAccountTypeColumn.setCellFactory(
                new Callback<TableColumn<OfferBookListItem, OfferBookListItem>, TableCell<OfferBookListItem,
                        OfferBookListItem>>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<OfferBookListItem, OfferBookListItem>() {
                            @Override
                            public void updateItem(final OfferBookListItem offerBookListItem, boolean empty) {
                                super.updateItem(offerBookListItem, empty);
                                setText(model.getBankAccountType(offerBookListItem));
                            }
                        };
                    }
                });
    }
}

