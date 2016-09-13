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

package io.bitsquare.gui.main.portfolio.openoffer;

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.withdrawal.WithdrawalView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.OfferDetailsWindow;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;

@FxmlView
public class OpenOffersView extends ActivatableViewAndModel<VBox, OpenOffersViewModel> {

    @FXML
    TableView<OpenOfferListItem> tableView;
    @FXML
    TableColumn<OpenOfferListItem, OpenOfferListItem> priceColumn, amountColumn, volumeColumn,
            marketColumn, directionColumn, dateColumn, offerIdColumn, removeItemColumn;
    private final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;
    private Preferences preferences;
    private SortedList<OpenOfferListItem> sortedList;

    @Inject
    public OpenOffersView(OpenOffersViewModel model, Navigation navigation, OfferDetailsWindow offerDetailsWindow, Preferences preferences) {
        super(model);
        this.navigation = navigation;
        this.offerDetailsWindow = offerDetailsWindow;
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        setOfferIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setMarketColumnCellFactory();
        setPriceColumnCellFactory();
        setAmountColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setRemoveColumnCellFactory();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label("No open offers available"));

        offerIdColumn.setComparator((o1, o2) -> o1.getOffer().getId().compareTo(o2.getOffer().getId()));
        directionColumn.setComparator((o1, o2) -> o1.getOffer().getDirection().compareTo(o2.getOffer().getDirection()));
        marketColumn.setComparator((o1, o2) -> model.getMarketLabel(o1).compareTo(model.getMarketLabel(o2)));
        amountColumn.setComparator((o1, o2) -> o1.getOffer().getAmount().compareTo(o2.getOffer().getAmount()));
        priceColumn.setComparator((o1, o2) -> {
            Fiat price1 = o1.getOffer().getPrice();
            Fiat price2 = o2.getOffer().getPrice();
            return price1 != null && price2 != null ? price1.compareTo(price2) : 0;
        });
        volumeColumn.setComparator((o1, o2) -> {
            Fiat offerVolume1 = o1.getOffer().getOfferVolume();
            Fiat offerVolume2 = o2.getOffer().getOfferVolume();
            return offerVolume1 != null && offerVolume2 != null ? offerVolume1.compareTo(offerVolume2) : 0;
        });
        dateColumn.setComparator((o1, o2) -> o1.getOffer().getDate().compareTo(o2.getOffer().getDate()));

        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(dateColumn);
    }

    @Override
    protected void activate() {
        sortedList = new SortedList<>(model.getList());
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
    }

    @Override
    protected void deactivate() {
        sortedList.comparatorProperty().unbind();
    }

    private void onRemoveOpenOffer(OpenOffer openOffer) {
        if (model.isBootstrapped()) {
            String key = "RemoveOfferWarning";
            if (preferences.showAgain(key))
                new Popup().warning("Are you sure you want to remove that offer?\n" +
                        "The offer fee you have paid will be lost if you remove that offer.")
                        .actionButtonText("Remove offer")
                        .onAction(() -> doRemoveOpenOffer(openOffer))
                        .closeButtonText("Don't remove the offer")
                        .dontShowAgainId(key, preferences)
                        .show();
            else
                doRemoveOpenOffer(openOffer);
        } else {
            new Popup().information("You need to wait until you are fully connected to the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
        }
    }

    private void doRemoveOpenOffer(OpenOffer openOffer) {
        model.onCancelOpenOffer(openOffer,
                () -> {
                    log.debug("Remove offer was successful");
                    String key = "WithdrawFundsAfterRemoveOfferInfo";
                    if (preferences.showAgain(key))
                        new Popup().instruction("You can withdraw the funds you paid in from the \"Fund/Available for withdrawal\" screen.")
                                .actionButtonText("Go to \"Funds/Available for withdrawal\"")
                                .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                                .dontShowAgainId(key, preferences)
                                .show();
                },
                (message) -> {
                    log.error(message);
                    new Popup().warning("Remove offer failed:\n" + message).show();
                });
    }

    private void setOfferIdColumnCellFactory() {
        offerIdColumn.setCellValueFactory((openOfferListItem) -> new ReadOnlyObjectWrapper<>(openOfferListItem.getValue()));
        offerIdColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem, OpenOfferListItem>>() {

                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem,
                            OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(model.getTradeId(item), true);
                                    field.setOnAction(event -> offerDetailsWindow.show(item.getOffer()));
                                    field.setTooltip(new Tooltip("Open popup for details"));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((openOfferListItem) -> new ReadOnlyObjectWrapper<>(openOfferListItem.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getDate(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
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
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
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
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getVolume(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getDirectionLabel(item));
                            }
                        };
                    }
                });
    }

    private void setMarketColumnCellFactory() {
        marketColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        marketColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem,
                        OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(
                            TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getMarketLabel(item));
                            }
                        };
                    }
                });
    }

    private void setRemoveColumnCellFactory() {
        removeItemColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        removeItemColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem, OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem, OpenOfferListItem> column) {
                        return new TableCell<OpenOfferListItem, OpenOfferListItem>() {
                            final ImageView iconView = new ImageView();
                            final Button button = new Button();

                            {
                                iconView.setId("image-remove");
                                button.setText("Remove");
                                button.setGraphic(iconView);
                                button.setMinWidth(70);
                            }

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null) {
                                    button.setOnAction(event -> onRemoveOpenOffer(item.getOpenOffer()));
                                    setGraphic(button);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
    }
}

