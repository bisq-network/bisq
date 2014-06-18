package io.bitsquare.gui.funds;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.Utils;
import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.BalanceListener;
import io.bitsquare.btc.ConfidenceListener;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.util.ConfidenceDisplay;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;


public class FundsController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(FundsController.class);

    private WalletFacade walletFacade;
    private ConfidenceDisplay confidenceDisplay;
    protected ObservableList<AddressListItem> addressList = FXCollections.observableArrayList();

    @FXML
    private TableView addressesTable;
    @FXML
    private TableColumn<String, AddressListItem> labelColumn, addressColumn, balanceColumn, copyColumn, confidenceColumn;
    @FXML
    private Button addNewAddressButton;
    @FXML
    private TextField labelTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FundsController(WalletFacade walletFacade)
    {
        this.walletFacade = walletFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        setBalanceColumnCellFactory();
        setCopyColumnCellFactory();
        setConfidenceColumnCellFactory();

        List<Address> addresses = walletFacade.getTradingAddresses();

        for (int i = 0; i < addresses.size(); i++)
        {
            Address address = addresses.get(i);
            String label;
            if (i == 0)
                label = "Registration";
            else
                label = "Trade " + i;
            addressList.add(new AddressListItem(label, address, false));
        }

        addressesTable.setItems(addressList);
        addressesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        //Platform.runLater(() -> walletFacade.triggerConfidenceNotificationForAllTransactions());
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

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onAddNewAddress(ActionEvent actionEvent)
    {
        Address address = walletFacade.createNewAddress();
        addressList.add(new AddressListItem("Trade " + (walletFacade.getTradingAddresses().size() - 1), address, false));
        // walletFacade.triggerConfidenceNotificationForAllTransactions();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setBalanceColumnCellFactory()
    {
        balanceColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        balanceColumn.setCellFactory(new Callback<TableColumn<String, AddressListItem>, TableCell<String, AddressListItem>>()
        {
            @Override
            public TableCell<String, AddressListItem> call(TableColumn<String, AddressListItem> column)
            {
                return new TableCell<String, AddressListItem>()
                {
                    Label balanceLabel;
                    BalanceListener balanceListener;

                    @Override
                    public void updateItem(final AddressListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null)
                        {
                            balanceListener = new BalanceListener(item.getAddress());
                            balanceLabel = new Label();
                            walletFacade.addBalanceListener(new BalanceListener(item.getAddress())
                            {
                                @Override
                                public void onBalanceChanged(BigInteger balance)
                                {
                                    updateBalance(balance, balanceLabel);
                                }
                            });

                            updateBalance(walletFacade.getBalance(item.getAddress()), balanceLabel);
                            setGraphic(balanceLabel);
                        }
                        else
                        {
                            if (balanceListener != null)
                                walletFacade.removeBalanceListener(balanceListener);

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
        copyColumn.setCellFactory(new Callback<TableColumn<String, AddressListItem>, TableCell<String, AddressListItem>>()
        {
            @Override
            public TableCell<String, AddressListItem> call(TableColumn<String, AddressListItem> column)
            {
                return new TableCell<String, AddressListItem>()
                {
                    Label copyIcon = new Label();

                    {
                        copyIcon.setId("copy-icon");
                        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
                        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
                    }

                    @Override
                    public void updateItem(final AddressListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null)
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
        confidenceColumn.setCellFactory(new Callback<TableColumn<String, AddressListItem>, TableCell<String, AddressListItem>>()
        {
            @Override
            public TableCell<String, AddressListItem> call(TableColumn<String, AddressListItem> column)
            {
                return new TableCell<String, AddressListItem>()
                {
                    ConfidenceListener confidenceListener;
                    ConfidenceProgressIndicator progressIndicator;

                    @Override
                    public void updateItem(final AddressListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null)
                        {
                            progressIndicator = new ConfidenceProgressIndicator();
                            Tooltip tooltip = new Tooltip("Not used yet");
                            progressIndicator.setProgress(0);
                            progressIndicator.setPrefHeight(30);
                            progressIndicator.setPrefWidth(30);
                            Tooltip.install(progressIndicator, tooltip);

                            confidenceListener = new ConfidenceListener(item.getAddress());
                            walletFacade.addConfidenceListener(new ConfidenceListener(item.getAddress())
                            {
                                @Override
                                public void onTransactionConfidenceChanged(TransactionConfidence confidence)
                                {
                                    updateConfidence(confidence, progressIndicator, tooltip);
                                }
                            });

                            updateConfidence(walletFacade.getConfidence(item.getAddress()), progressIndicator, tooltip);
                            setGraphic(progressIndicator);
                        }
                        else
                        {
                            if (confidenceListener != null)
                                walletFacade.removeConfidenceListener(confidenceListener);

                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }

    private void updateBalance(BigInteger balance, Label balanceLabel)
    {
        if (balance != null)
            balanceLabel.setText(Utils.bitcoinValueToFriendlyString(balance));
    }

    private void updateConfidence(TransactionConfidence confidence, ConfidenceProgressIndicator progressIndicator, Tooltip tooltip)
    {
        if (confidence != null)
        {
            switch (confidence.getConfidenceType())
            {
                case UNKNOWN:
                    tooltip.setText("Unknown transaction status");
                    progressIndicator.setProgress(0);
                    break;
                case PENDING:
                    tooltip.setText("Seen by " + confidence.numBroadcastPeers() + " peer(s) / 0 confirmations");
                    progressIndicator.setProgress(-1.0);
                    break;
                case BUILDING:
                    tooltip.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                    progressIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                    break;
                case DEAD:
                    tooltip.setText("Transaction is invalid.");
                    break;
            }
        }
    }

}

