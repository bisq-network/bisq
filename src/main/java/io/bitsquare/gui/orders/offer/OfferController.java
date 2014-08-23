package io.bitsquare.gui.orders.offer;

import io.bitsquare.gui.CachedViewController;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.TradeManager;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.util.Callback;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("EmptyMethod")
public class OfferController extends CachedViewController
{
    private static final Logger log = LoggerFactory.getLogger(OfferController.class);
    private final TradeManager tradeManager;
    private ObservableList<OfferListItem> offerListItems;

    @FXML private TableColumn<String, OfferListItem> offerIdColumn, dateColumn, amountColumn, priceColumn, volumeColumn, removeColumn;
    @FXML private TableView<OfferListItem> offerTable;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private OfferController(TradeManager tradeManager)
    {
        this.tradeManager = tradeManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        super.initialize(url, rb);

        setOfferIdColumnColumnCellFactory();
        setRemoveColumnCellFactory();
        offerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    @Override
    public void deactivate()
    {
        super.deactivate();
    }

    @Override
    public void activate()
    {
        super.activate();

        offerListItems = FXCollections.observableArrayList();
        Map<String, Offer> offerMap = tradeManager.getOffers();
        List<Offer> offerList = new ArrayList<>(offerMap.values());
        offerListItems.addAll(offerList.stream().map(OfferListItem::new).collect(Collectors.toList()));
        offerTable.setItems(offerListItems);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // GUI Event handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removeOffer(OfferListItem offerListItem)
    {
        tradeManager.removeOffer(offerListItem.getOffer());
        offerListItems.remove(offerListItem);
    }

    private void openOfferDetails(OfferListItem offerListItem)
    {

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setOfferIdColumnColumnCellFactory()
    {
        offerIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper(offerListItem.getValue()));
        offerIdColumn.setCellFactory(new Callback<TableColumn<String, OfferListItem>, TableCell<String, OfferListItem>>()
        {

            @Override
            public TableCell<String, OfferListItem> call(TableColumn<String, OfferListItem> column)
            {
                return new TableCell<String, OfferListItem>()
                {
                    Hyperlink hyperlink;

                    @Override
                    public void updateItem(final OfferListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null && !empty)
                        {
                            hyperlink = new Hyperlink(item.getOfferId());
                            //hyperlink.getStyleClass().setAll("aaa");
                            Tooltip tooltip = new Tooltip(item.getOfferId());
                            Tooltip.install(hyperlink, tooltip);
                            hyperlink.setOnAction(event -> openOfferDetails(item));
                            setGraphic(hyperlink);
                        }
                        else
                        {
                            setGraphic(null);
                            setId(null);
                        }
                    }
                };
            }
        });
    }

    private void setRemoveColumnCellFactory()
    {
        removeColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper(offerListItem.getValue()));
        removeColumn.setCellFactory(new Callback<TableColumn<String, OfferListItem>, TableCell<String, OfferListItem>>()
        {

            @Override
            public TableCell<String, OfferListItem> call(TableColumn<String, OfferListItem> directionColumn)
            {
                return new TableCell<String, OfferListItem>()
                {
                    final ImageView iconView = ImageUtil.getIconImageView(ImageUtil.REMOVE);
                    final Button button = new Button();

                    {
                        button.setText("Remove");
                        button.setGraphic(iconView);
                        button.setMinWidth(70);
                    }

                    @Override
                    public void updateItem(final OfferListItem offerListItem, boolean empty)
                    {
                        super.updateItem(offerListItem, empty);

                        if (offerListItem != null)
                        {
                            button.setOnAction(event -> removeOffer(offerListItem));
                            setGraphic(button);
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }
}

