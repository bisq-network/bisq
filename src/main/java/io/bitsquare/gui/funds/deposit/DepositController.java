package io.bitsquare.gui.funds.deposit;

import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class DepositController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(DepositController.class);

    private WalletFacade walletFacade;
    protected ObservableList<DepositListItem> addressList;

    @FXML
    private TableView tableView;
    @FXML
    private TableColumn<String, DepositListItem> labelColumn, addressColumn, balanceColumn, copyColumn, confidenceColumn;
    @FXML
    private Button addNewAddressButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DepositController(WalletFacade walletFacade)
    {
        this.walletFacade = walletFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        awake();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        setLabelColumnCellFactory();
        setBalanceColumnCellFactory();
        setCopyColumnCellFactory();
        setConfidenceColumnCellFactory();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
    }

    @Override
    public void cleanup()
    {
        for (int i = 0; i < addressList.size(); i++)
        {
            addressList.get(i).cleanup();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Hibernate
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sleep()
    {
        cleanup();
    }

    @Override
    public void awake()
    {
        List<AddressEntry> addressEntryList = walletFacade.getAddressEntryList();
        addressList = FXCollections.observableArrayList();
        for (int i = 0; i < addressEntryList.size(); i++)
        {
            addressList.add(new DepositListItem(addressEntryList.get(i), walletFacade));
        }

        tableView.setItems(addressList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onAddNewTradeAddress(ActionEvent actionEvent)
    {
        addressList.add(new DepositListItem(walletFacade.getNewTradeAddressInfo(), walletFacade));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cell factories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setLabelColumnCellFactory()
    {
        labelColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        labelColumn.setCellFactory(new Callback<TableColumn<String, DepositListItem>, TableCell<String, DepositListItem>>()
        {
            @Override
            public TableCell<String, DepositListItem> call(TableColumn<String, DepositListItem> column)
            {
                return new TableCell<String, DepositListItem>()
                {
                    Hyperlink hyperlink;

                    @Override
                    public void updateItem(final DepositListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null && !empty)
                        {
                            hyperlink = new Hyperlink(item.getLabel());
                            hyperlink.setId("id-link");
                            if (item.getAddressEntry().getTradeId() != null)
                            {
                                Tooltip tooltip = new Tooltip(item.getAddressEntry().getTradeId());
                                Tooltip.install(hyperlink, tooltip);

                                hyperlink.setOnAction(new EventHandler<ActionEvent>()
                                {
                                    @Override
                                    public void handle(ActionEvent event)
                                    {
                                        log.info("Show trade details " + item.getAddressEntry().getTradeId());
                                    }
                                });
                            }
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

    private void setBalanceColumnCellFactory()
    {
        balanceColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        balanceColumn.setCellFactory(new Callback<TableColumn<String, DepositListItem>, TableCell<String, DepositListItem>>()
        {
            @Override
            public TableCell<String, DepositListItem> call(TableColumn<String, DepositListItem> column)
            {
                return new TableCell<String, DepositListItem>()
                {
                    @Override
                    public void updateItem(final DepositListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null && !empty)
                        {
                            setGraphic(item.getBalanceLabel());
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

    private void setCopyColumnCellFactory()
    {
        copyColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        copyColumn.setCellFactory(new Callback<TableColumn<String, DepositListItem>, TableCell<String, DepositListItem>>()
        {
            @Override
            public TableCell<String, DepositListItem> call(TableColumn<String, DepositListItem> column)
            {
                return new TableCell<String, DepositListItem>()
                {
                    Label copyIcon = new Label();

                    {
                        copyIcon.getStyleClass().add("copy-icon");
                        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
                        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
                    }

                    @Override
                    public void updateItem(final DepositListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null && !empty)
                        {
                            setGraphic(copyIcon);
                            copyIcon.setOnMouseClicked(e -> {
                                Clipboard clipboard = Clipboard.getSystemClipboard();
                                ClipboardContent content = new ClipboardContent();
                                content.putString(item.addressStringProperty().get());
                                clipboard.setContent(content);
                            });

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

    private void setConfidenceColumnCellFactory()
    {
        confidenceColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        confidenceColumn.setCellFactory(new Callback<TableColumn<String, DepositListItem>, TableCell<String, DepositListItem>>()
        {
            @Override
            public TableCell<String, DepositListItem> call(TableColumn<String, DepositListItem> column)
            {
                return new TableCell<String, DepositListItem>()
                {

                    @Override
                    public void updateItem(final DepositListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null && !empty)
                        {
                            setGraphic(item.getProgressIndicator());
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

