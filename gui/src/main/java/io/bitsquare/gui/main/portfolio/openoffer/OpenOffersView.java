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
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.withdrawal.WithdrawalView;
import io.bitsquare.gui.popups.OfferDetailsPopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.trade.offer.OpenOffer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import javax.inject.Inject;

@FxmlView
public class OpenOffersView extends ActivatableViewAndModel<VBox, OpenOffersViewModel> {

    @FXML
    TableView<OpenOfferListItem> table;
    @FXML
    TableColumn<OpenOfferListItem, OpenOfferListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, dateColumn, offerIdColumn, removeItemColumn;
    private final Navigation navigation;
    private final OfferDetailsPopup offerDetailsPopup;

    @Inject
    public OpenOffersView(OpenOffersViewModel model, Navigation navigation, OfferDetailsPopup offerDetailsPopup) {
        super(model);
        this.navigation = navigation;
        this.offerDetailsPopup = offerDetailsPopup;
    }

    @Override
    public void initialize() {
        setOfferIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setRemoveColumnCellFactory();

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No open offers available"));
    }

    @Override
    protected void activate() {
        table.setItems(model.getList());
    }

    private void onRemoveOpenOffer(OpenOffer openOffer) {
        if (model.isBootstrapped()) {
            new Popup().warning("Are you sure you want to remove that offer?\n" +
                    "The offer fee you have paid will be lost if you remove that offer.")
                    .actionButtonText("Remove offer")
                    .onAction(() -> doRemoveOpenOffer(openOffer))
                    .closeButtonText("Don't remove the offer")
                    .show();
        } else {
            new Popup().warning("You need to wait until your client is bootstrapped in the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
        }
    }

    private void doRemoveOpenOffer(OpenOffer openOffer) {
        model.onCancelOpenOffer(openOffer,
                () -> {
                    log.debug("Remove offer was successful");
                    new Popup().information("You can withdraw the funds you paid in from the funds screens.")
                            .onClose(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
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

                            @Override
                            public void updateItem(final OpenOfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    Hyperlink hyperlink = new Hyperlink(model.getTradeId(item));
                                    Tooltip.install(hyperlink, new Tooltip(model.getTradeId(item)));
                                    hyperlink.setOnAction(event -> offerDetailsPopup.show(item.getOffer()));
                                    setGraphic(hyperlink);
                                } else {
                                    setGraphic(null);
                                    setId(null);
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

    private void setRemoveColumnCellFactory() {
        removeItemColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        removeItemColumn.setCellFactory(
                new Callback<TableColumn<OpenOfferListItem, OpenOfferListItem>, TableCell<OpenOfferListItem, OpenOfferListItem>>() {
                    @Override
                    public TableCell<OpenOfferListItem, OpenOfferListItem> call(TableColumn<OpenOfferListItem,
                            OpenOfferListItem> directionColumn) {
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

