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

package io.bitsquare.gui.main.portfolio.offer;

import io.bitsquare.common.viewfx.view.ActivatableViewAndModel;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.withdrawal.WithdrawalView;
import io.bitsquare.offer.Offer;
import io.bitsquare.util.Utilities;

import javax.inject.Inject;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

@FxmlView
public class OffersView extends ActivatableViewAndModel<GridPane, OffersViewModel> {

    @FXML TableView<OfferListItem> table;
    @FXML TableColumn<OfferListItem, OfferListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, dateColumn, offerIdColumn, removeItemColumn;
    private Navigation navigation;

    @Inject
    public OffersView(OffersViewModel model, Navigation navigation) {
        super(model);
        this.navigation = navigation;
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
    public void doActivate() {
        table.setItems(model.getList());
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

    private void openOfferDetails(OfferListItem item) {
        // TODO Open popup with details view
        log.debug("openOfferDetails " + item);
        Utilities.copyToClipboard(item.getOffer().getId());
        Popups.openWarningPopup("Under construction",
                "The offer ID was copied to the clipboard. " +
                        "Use that to identify your trading peer in the IRC chat room \n\n" +
                        "Later this will open a details popup but that is not implemented yet.");
    }

    private void setOfferIdColumnCellFactory() {
        offerIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        offerIdColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {

                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem,
                            OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    hyperlink = new Hyperlink(model.getTradeId(item));
                                    hyperlink.setId("id-link");
                                    Tooltip.install(hyperlink, new Tooltip(model.getTradeId(item)));
                                    hyperlink.setOnAction(event -> openOfferDetails(item));
                                    setGraphic(hyperlink);
                                }
                                else {
                                    setGraphic(null);
                                    setId(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
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
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
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
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
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
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
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
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem,
                        OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(
                            TableColumn<OfferListItem, OfferListItem> column) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
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
                new Callback<TableColumn<OfferListItem, OfferListItem>, TableCell<OfferListItem, OfferListItem>>() {
                    @Override
                    public TableCell<OfferListItem, OfferListItem> call(TableColumn<OfferListItem,
                            OfferListItem> directionColumn) {
                        return new TableCell<OfferListItem, OfferListItem>() {
                            final ImageView iconView = new ImageView();
                            final Button button = new Button();

                            {
                                iconView.setId("image-remove");
                                button.setText("Remove");
                                button.setGraphic(iconView);
                                button.setMinWidth(70);
                            }

                            @Override
                            public void updateItem(final OfferListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null) {
                                    button.setOnAction(event -> onCancelOpenOffer(item.getOffer()));
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
}

