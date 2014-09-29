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

package io.bitsquare.gui.main.orders.closed;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.components.Popups;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClosedTradesViewCB extends CachedViewCB<ClosedTradesPM> {
    private static final Logger log = LoggerFactory.getLogger(ClosedTradesViewCB.class);

    @FXML TableColumn<ClosedTradesListItem, ClosedTradesListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, dateColumn, tradeIdColumn;
    @FXML TableView<ClosedTradesListItem> table;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ClosedTradesViewCB(ClosedTradesPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No closed trades available"));

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        table.setItems(presentationModel.getList());
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
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void openOfferDetails(ClosedTradesListItem item) {
        // TODO Open popup with details view
        log.debug("Trade details " + item);
        Popups.openWarningPopup("Under construction", "This will open a details " +
                "popup but that is not implemented yet.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradesListItem, ClosedTradesListItem>, TableCell<ClosedTradesListItem,
                        ClosedTradesListItem>>() {

                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(TableColumn<ClosedTradesListItem,
                            ClosedTradesListItem> column) {
                        return new TableCell<ClosedTradesListItem, ClosedTradesListItem>() {
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    hyperlink = new Hyperlink(presentationModel.getTradeId(item));
                                    hyperlink.setId("id-link");
                                    Tooltip.install(hyperlink, new Tooltip(presentationModel.getTradeId(item)));
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
                new Callback<TableColumn<ClosedTradesListItem, ClosedTradesListItem>, TableCell<ClosedTradesListItem,
                        ClosedTradesListItem>>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<ClosedTradesListItem, ClosedTradesListItem>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(presentationModel.getDate(item));
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
                new Callback<TableColumn<ClosedTradesListItem, ClosedTradesListItem>, TableCell<ClosedTradesListItem,
                        ClosedTradesListItem>>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<ClosedTradesListItem, ClosedTradesListItem>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
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
                new Callback<TableColumn<ClosedTradesListItem, ClosedTradesListItem>, TableCell<ClosedTradesListItem,
                        ClosedTradesListItem>>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<ClosedTradesListItem, ClosedTradesListItem>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
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
                new Callback<TableColumn<ClosedTradesListItem, ClosedTradesListItem>, TableCell<ClosedTradesListItem,
                        ClosedTradesListItem>>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<ClosedTradesListItem, ClosedTradesListItem>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(presentationModel.getVolume(item));
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
                new Callback<TableColumn<ClosedTradesListItem, ClosedTradesListItem>, TableCell<ClosedTradesListItem,
                        ClosedTradesListItem>>() {
                    @Override
                    public TableCell<ClosedTradesListItem, ClosedTradesListItem> call(
                            TableColumn<ClosedTradesListItem, ClosedTradesListItem> column) {
                        return new TableCell<ClosedTradesListItem, ClosedTradesListItem>() {
                            @Override
                            public void updateItem(final ClosedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(presentationModel.getDirectionLabel(item));
                            }
                        };
                    }
                });
    }
}

