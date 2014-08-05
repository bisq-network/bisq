package io.bitsquare.gui.funds.withdrawal;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BtcValidator;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.popups.Popups;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.gui.util.BitSquareValidator;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Callback;
import javax.inject.Inject;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WithdrawalController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(WithdrawalController.class);


    private final WalletFacade walletFacade;
    private ObservableList<WithdrawalListItem> addressList;

    @FXML
    private TableView<WithdrawalListItem> tableView;
    @FXML
    private TableColumn<String, WithdrawalListItem> labelColumn, addressColumn, balanceColumn, copyColumn, confidenceColumn;
    @FXML
    private Button addNewAddressButton;
    @FXML
    private TextField withdrawFromTextField, withdrawToTextField, amountTextField, changeAddressTextField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private WithdrawalController(WalletFacade walletFacade)
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

        tableView.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != null)
            {
                BitSquareValidator.resetTextFields(withdrawFromTextField, withdrawToTextField, amountTextField, changeAddressTextField);

                if (Coin.ZERO.compareTo(newValue.getBalance()) <= 0)
                {
                    amountTextField.setText(newValue.getBalance().toPlainString());
                    withdrawFromTextField.setText(newValue.getAddressEntry().getAddressString());
                    changeAddressTextField.setText(newValue.getAddressEntry().getAddressString());
                }
                else
                {
                    withdrawFromTextField.setText("");
                    withdrawFromTextField.setPromptText("No fund to withdrawal on that address.");
                    amountTextField.setText("");
                    amountTextField.setPromptText("Invalid amount");
                }
            }
        });
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
        for (WithdrawalListItem anAddressList : addressList)
        {
            anAddressList.cleanup();
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
        addressList.addAll(addressEntryList.stream().map(anAddressEntryList -> new WithdrawalListItem(anAddressEntryList, walletFacade)).collect(Collectors.toList()));

        tableView.setItems(addressList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onWithdraw()
    {
        try
        {
            BitSquareValidator.textFieldsNotEmpty(amountTextField, withdrawFromTextField, withdrawToTextField, changeAddressTextField);
            BitSquareValidator.textFieldsHasDoubleValueWithReset(amountTextField);

            Coin amount = BitSquareFormatter.parseBtcToCoin(amountTextField.getText());
            if (BtcValidator.isMinSpendableAmount(amount))
            {
                FutureCallback<Transaction> callback = new FutureCallback<Transaction>()
                {
                    @Override
                    public void onSuccess(@javax.annotation.Nullable Transaction transaction)
                    {
                        BitSquareValidator.resetTextFields(withdrawFromTextField, withdrawToTextField, amountTextField, changeAddressTextField);
                        if (transaction != null)
                        {
                            log.info("onWithdraw onSuccess txid:" + transaction.getHashAsString());
                        }
                    }

                    @Override
                    public void onFailure(Throwable t)
                    {
                        log.debug("onWithdraw onFailure");
                    }
                };

                Action response = Popups.openConfirmPopup("Withdrawal request", "Confirm your request", "Your withdrawal request:\n\n" +
                        "Amount: " + amountTextField.getText() + " BTC\n" +
                        "Sending address: " + withdrawFromTextField.getText() + "\n" +
                        "Receiving address: " + withdrawToTextField.getText() + "\n" +
                        "Transaction fee: " + BitSquareFormatter.formatCoinToBtcWithCode(FeePolicy.TX_FEE) + "\n" +
                        "You receive in total: " + BitSquareFormatter.formatCoinToBtcWithCode(amount.subtract(FeePolicy.TX_FEE)) + " BTC\n\n" +
                        "Are you sure you withdraw that amount?");
                if (response == Dialog.Actions.OK)
                {
                    try
                    {
                        walletFacade.sendFunds(withdrawFromTextField.getText(), withdrawToTextField.getText(), changeAddressTextField.getText(), amount, callback);
                    } catch (AddressFormatException e)
                    {
                        Popups.openErrorPopup("Address invalid", "The address is not correct. Please check the address format.");

                    } catch (InsufficientMoneyException e)
                    {
                        Popups.openInsufficientMoneyPopup();
                    } catch (IllegalArgumentException e)
                    {
                        Popups.openErrorPopup("Wrong inputs", "Please check the inputs.");
                    }
                }

            }
            else
            {
                Popups.openErrorPopup("Insufficient amount", "The amount to transfer is lower the the transaction fee and the min. possible tx value.");
            }

        } catch (BitSquareValidator.ValidationException e)
        {
            log.trace(e.toString());
        }
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
        labelColumn.setCellFactory(new Callback<TableColumn<String, WithdrawalListItem>, TableCell<String, WithdrawalListItem>>()
        {

            @Override
            public TableCell<String, WithdrawalListItem> call(TableColumn<String, WithdrawalListItem> column)
            {
                return new TableCell<String, WithdrawalListItem>()
                {

                    Hyperlink hyperlink;

                    @Override
                    public void updateItem(final WithdrawalListItem item, boolean empty)
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

                                hyperlink.setOnAction(event -> log.info("Show trade details " + item.getAddressEntry().getTradeId()));
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
        balanceColumn.setCellFactory(new Callback<TableColumn<String, WithdrawalListItem>, TableCell<String, WithdrawalListItem>>()
        {

            @Override
            public TableCell<String, WithdrawalListItem> call(TableColumn<String, WithdrawalListItem> column)
            {
                return new TableCell<String, WithdrawalListItem>()
                {
                    @Override
                    public void updateItem(final WithdrawalListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);
                        setGraphic((item != null && !empty) ? item.getBalanceLabel() : null);
                    }
                };
            }
        });
    }

    private void setCopyColumnCellFactory()
    {
        copyColumn.setCellValueFactory((addressListItem) -> new ReadOnlyObjectWrapper(addressListItem.getValue()));
        copyColumn.setCellFactory(new Callback<TableColumn<String, WithdrawalListItem>, TableCell<String, WithdrawalListItem>>()
        {

            @Override
            public TableCell<String, WithdrawalListItem> call(TableColumn<String, WithdrawalListItem> column)
            {
                return new TableCell<String, WithdrawalListItem>()
                {
                    final Label copyIcon = new Label();

                    {
                        copyIcon.getStyleClass().add("copy-icon");
                        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
                        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
                    }

                    @Override
                    public void updateItem(final WithdrawalListItem item, boolean empty)
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
        confidenceColumn.setCellFactory(new Callback<TableColumn<String, WithdrawalListItem>, TableCell<String, WithdrawalListItem>>()
        {

            @Override
            public TableCell<String, WithdrawalListItem> call(TableColumn<String, WithdrawalListItem> column)
            {
                return new TableCell<String, WithdrawalListItem>()
                {

                    @Override
                    public void updateItem(final WithdrawalListItem item, boolean empty)
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


