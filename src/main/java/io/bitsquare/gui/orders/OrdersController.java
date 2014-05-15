package io.bitsquare.gui.orders;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.Trading;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;

public class OrdersController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(OrdersController.class);

    private Trading trading;
    private Trade currentTrade;

    private Image buyIcon = Icons.getIconImage(Icons.BUY);
    private Image sellIcon = Icons.getIconImage(Icons.SELL);

    @FXML
    private TableView openTradesTable;
    @FXML
    private TableColumn<String, TradesTableItem> directionColumn, countryColumn, bankAccountTypeColumn, priceColumn, amountColumn, volumeColumn, statusColumn, selectColumn;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private Label confidenceLabel, txIDCopyIcon, holderNameCopyIcon, primaryBankAccountIDCopyIcon, secondaryBankAccountIDCopyIcon;
    @FXML
    private TextField txIDTextField, bankAccountTypeTextField, holderNameTextField, primaryBankAccountIDTextField, secondaryBankAccountIDTextField;
    @FXML
    private Button bankTransferInitedButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OrdersController(Trading trading)
    {
        this.trading = trading;
    }


    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        Map<String, Trade> trades = trading.getTrades();
        List<Trade> tradeList = new ArrayList<>(trades.values());
        ObservableList<TradesTableItem> tradeItems = FXCollections.observableArrayList();
        for (Iterator<Trade> iterator = tradeList.iterator(); iterator.hasNext(); )
        {
            Trade trade = iterator.next();
            tradeItems.add(new TradesTableItem(trade));
        }

        setCountryColumnCellFactory();
        setBankAccountTypeColumnCellFactory();
        setDirectionColumnCellFactory();
        setSelectColumnCellFactory();

        openTradesTable.setItems(tradeItems);
        openTradesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        openTradesTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue instanceof TradesTableItem)
            {
                TradesTableItem tradesTableItem = (TradesTableItem) newValue;
                fillData(tradesTableItem.getTrade());
            }
        });

        initCopyIcons();
    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
    }

    @Override
    public void cleanup()
    {

    }

    public void bankTransferInited(ActionEvent actionEvent)
    {
        trading.onBankTransferInited(currentTrade.getUid());
    }

    private void updateTx(Trade trade)
    {
        Transaction transaction = trade.getDepositTransaction();
        String txID = "";
        if (transaction != null)
        {
            txID = transaction.getHashAsString();

            transaction.getConfidence().addEventListener(new TransactionConfidence.Listener()
            {
                @Override
                public void onConfidenceChanged(Transaction tx, ChangeReason reason)
                {
                    updateConfidence(tx);
                }
            });
        }
        else
        {
            updateConfidence(transaction);
        }
        txIDTextField.setText(txID);
    }

    private void updateConfidence(Transaction tx)
    {
        TransactionConfidence confidence = tx.getConfidence();

        switch (confidence.getConfidenceType())
        {
            case UNKNOWN:
                confidenceLabel.setText("");
                progressIndicator.setProgress(0);
                break;
            case PENDING:
                confidenceLabel.setText("Seen by " + confidence.numBroadcastPeers() + " peer(s)");
                progressIndicator.setProgress(-1);
                break;
            case BUILDING:
                bankTransferInitedButton.setOpacity(1);
                confidenceLabel.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                progressIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                break;
            case DEAD:
                confidenceLabel.setText("Transaction is invalid.");
                break;
        }
    }

    private void fillData(Trade trade)
    {
        currentTrade = trade;
        Transaction transaction = trade.getDepositTransaction();
        if (transaction == null)
        {
            trade.getDepositTxChangedProperty().addListener(new ChangeListener<Boolean>()
            {
                @Override
                public void changed(ObservableValue<? extends Boolean> observableValue, Boolean aBoolean, Boolean aBoolean2)
                {
                    updateTx(trade);
                }
            });
        }
        else
        {
            updateTx(trade);
        }

        // back details
        BankAccount bankAccount = trade.getContract().getTakerBankAccount();
        bankAccountTypeTextField.setText(bankAccount.getBankAccountType().getType().toString());
        holderNameTextField.setText(bankAccount.getAccountHolderName());
        primaryBankAccountIDTextField.setText(bankAccount.getAccountPrimaryID());
        secondaryBankAccountIDTextField.setText(bankAccount.getAccountSecondaryID());
    }
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setCountryColumnCellFactory()
    {
        countryColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        countryColumn.setCellFactory(new Callback<TableColumn<String, TradesTableItem>, TableCell<String, TradesTableItem>>()
        {
            @Override
            public TableCell<String, TradesTableItem> call(TableColumn<String, TradesTableItem> directionColumn)
            {
                return new TableCell<String, TradesTableItem>()
                {
                    final HBox hBox = new HBox();

                    {
                        hBox.setSpacing(3);
                        hBox.setAlignment(Pos.CENTER);
                        setGraphic(hBox);
                    }

                    @Override
                    public void updateItem(final TradesTableItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        hBox.getChildren().clear();
                        if (orderBookListItem != null)
                        {
                            Locale countryLocale = orderBookListItem.getTrade().getOffer().getBankAccountCountryLocale();
                            try
                            {
                                hBox.getChildren().add(Icons.getIconImageView("/images/countries/" + countryLocale.getCountry().toLowerCase() + ".png"));

                            } catch (Exception e)
                            {
                                log.warn("Country icon not found: " + "/images/countries/" + countryLocale.getCountry().toLowerCase() + ".png country name: " + countryLocale.getDisplayCountry());
                            }
                            Tooltip.install(this, new Tooltip(countryLocale.getDisplayCountry()));
                        }
                    }
                };
            }
        });
    }

    private void setBankAccountTypeColumnCellFactory()
    {
        bankAccountTypeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        bankAccountTypeColumn.setCellFactory(new Callback<TableColumn<String, TradesTableItem>, TableCell<String, TradesTableItem>>()
        {
            @Override
            public TableCell<String, TradesTableItem> call(TableColumn<String, TradesTableItem> directionColumn)
            {
                return new TableCell<String, TradesTableItem>()
                {
                    @Override
                    public void updateItem(final TradesTableItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        if (orderBookListItem != null)
                        {
                            BankAccountType.BankAccountTypeEnum bankAccountTypeEnum = orderBookListItem.getTrade().getOffer().getBankAccountTypeEnum();
                            setText(Localisation.get(bankAccountTypeEnum.toString()));
                        }
                        else
                        {
                            setText("");
                        }
                    }
                };
            }
        });
    }

    private void setDirectionColumnCellFactory()
    {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        directionColumn.setCellFactory(new Callback<TableColumn<String, TradesTableItem>, TableCell<String, TradesTableItem>>()
        {
            @Override
            public TableCell<String, TradesTableItem> call(TableColumn<String, TradesTableItem> directionColumn)
            {
                return new TableCell<String, TradesTableItem>()
                {
                    final ImageView iconView = new ImageView();
                    final Button button = new Button();

                    {
                        button.setGraphic(iconView);
                        button.setMinWidth(70);
                    }

                    @Override
                    public void updateItem(final TradesTableItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        if (orderBookListItem != null)
                        {
                            String title;
                            Image icon;
                            Offer offer = orderBookListItem.getTrade().getOffer();

                            if (offer.getDirection() == Direction.SELL)
                            {
                                icon = buyIcon;
                                title = io.bitsquare.gui.util.Formatter.formatDirection(Direction.BUY, true);
                            }
                            else
                            {
                                icon = sellIcon;
                                title = io.bitsquare.gui.util.Formatter.formatDirection(Direction.SELL, true);
                            }
                            button.setDisable(true);
                            iconView.setImage(icon);
                            button.setText(title);
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

    private void setSelectColumnCellFactory()
    {
        selectColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        selectColumn.setCellFactory(new Callback<TableColumn<String, TradesTableItem>, TableCell<String, TradesTableItem>>()
        {
            @Override
            public TableCell<String, TradesTableItem> call(TableColumn<String, TradesTableItem> directionColumn)
            {
                return new TableCell<String, TradesTableItem>()
                {
                    final Button button = new Button("Select");

                    @Override
                    public void updateItem(final TradesTableItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        if (orderBookListItem != null)
                        {
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


    private void initCopyIcons()
    {
        AwesomeDude.setIcon(txIDCopyIcon, AwesomeIcon.COPY);
        txIDCopyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(txIDTextField.getText());
            clipboard.setContent(content);
        });

        AwesomeDude.setIcon(holderNameCopyIcon, AwesomeIcon.COPY);
        holderNameCopyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(holderNameCopyIcon.getText());
            clipboard.setContent(content);
        });

        AwesomeDude.setIcon(primaryBankAccountIDCopyIcon, AwesomeIcon.COPY);
        primaryBankAccountIDCopyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(primaryBankAccountIDCopyIcon.getText());
            clipboard.setContent(content);
        });

        AwesomeDude.setIcon(secondaryBankAccountIDCopyIcon, AwesomeIcon.COPY);
        secondaryBankAccountIDCopyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(secondaryBankAccountIDCopyIcon.getText());
            clipboard.setContent(content);
        });
    }

}

