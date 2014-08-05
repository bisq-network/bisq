package io.bitsquare.gui.orders.pending;

import com.google.bitcoin.core.*;
import com.google.bitcoin.script.Script;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.gui.util.ConfidenceDisplay;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Localisation;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.Trading;
import io.bitsquare.util.AWTSystemTray;
import java.net.URL;
import java.util.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PendingTradeController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(PendingTradeController.class);

    private Trading trading;
    private WalletFacade walletFacade;

    private Trade currentTrade;
    private NavigationController navigationController;
    private Image buyIcon = Icons.getIconImage(Icons.BUY);
    private Image sellIcon = Icons.getIconImage(Icons.SELL);
    private ConfidenceDisplay confidenceDisplay;

    @FXML
    private VBox rootContainer;
    @FXML
    private TableView openTradesTable;
    @FXML
    private TableColumn<String, PendingTradesListItem> directionColumn, countryColumn, bankAccountTypeColumn, priceColumn, amountColumn, volumeColumn, statusColumn, selectColumn;
    @FXML
    private ConfidenceProgressIndicator progressIndicator;
    @FXML
    private Label txTitleLabel, txHeaderLabel, confirmationLabel, txIDCopyIcon, holderNameCopyIcon, primaryBankAccountIDCopyIcon, secondaryBankAccountIDCopyIcon, bankAccountDetailsHeaderLabel,
            bankAccountTypeTitleLabel, holderNameTitleLabel, primaryBankAccountIDTitleLabel, secondaryBankAccountIDTitleLabel;
    @FXML
    private TextField txTextField, bankAccountTypeTextField, holderNameTextField, primaryBankAccountIDTextField, secondaryBankAccountIDTextField;
    @FXML
    private Button bankTransferInitedButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradeController(Trading trading, WalletFacade walletFacade)
    {
        this.trading = trading;
        this.walletFacade = walletFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        awake();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void cleanup()
    {
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
        Map<String, Trade> trades = trading.getTrades();
        List<Trade> tradeList = new ArrayList<>(trades.values());
        ObservableList<PendingTradesListItem> tradeItems = FXCollections.observableArrayList();
        for (Iterator<Trade> iterator = tradeList.iterator(); iterator.hasNext(); )
        {
            Trade trade = iterator.next();
            tradeItems.add(new PendingTradesListItem(trade));
        }

        setCountryColumnCellFactory();
        setBankAccountTypeColumnCellFactory();
        setDirectionColumnCellFactory();
        setSelectColumnCellFactory();

        openTradesTable.setItems(tradeItems);
        openTradesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        openTradesTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue instanceof PendingTradesListItem)
            {
                showTradeDetails((PendingTradesListItem) newValue);
            }
        });

        trading.getNewTradeProperty().addListener((observableValue, oldTradeId, newTradeId) -> {
            Trade newTrade = trading.getTrade(newTradeId);
            if (newTrade != null)
            {
                tradeItems.add(new PendingTradesListItem(newTrade));
            }
        });

        initCopyIcons();

        // select
        Optional<PendingTradesListItem> currentTradeItemOptional = tradeItems.stream()
                                                                             .filter((e) -> trading.getPendingTrade() != null && e.getTrade().getId().equals(trading.getPendingTrade().getId()))
                                                                             .findFirst();
        if (currentTradeItemOptional.isPresent())
        {
            openTradesTable.getSelectionModel().select(currentTradeItemOptional.get());
        }

        tradeItems.addListener((ListChangeListener<PendingTradesListItem>) change -> {
            if (openTradesTable.getSelectionModel().getSelectedItem() == null && tradeItems.size() > 0)
            {
                openTradesTable.getSelectionModel().select(0);
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // GUI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void bankTransferInited()
    {
        trading.bankTransferInited(currentTrade.getId());
        bankTransferInitedButton.setDisable(true);
    }

    public void close()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showTradeDetails(PendingTradesListItem tradesTableItem)
    {
        fillData(tradesTableItem.getTrade());
    }

    private void updateTx(Transaction transaction)
    {
        txTextField.setText(transaction.getHashAsString());

        confidenceDisplay = new ConfidenceDisplay(walletFacade.getWallet(), confirmationLabel, transaction, progressIndicator);

        int depthInBlocks = transaction.getConfidence().getDepthInBlocks();
        bankTransferInitedButton.setDisable(depthInBlocks == 0);

        walletFacade.getWallet().addEventListener(new WalletEventListener()
        {
            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
            {
                int depthInBlocks = tx.getConfidence().getDepthInBlocks();
                bankTransferInitedButton.setDisable(depthInBlocks == 0);
            }

            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance)
            {
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance)
            {
            }

            @Override
            public void onReorganize(Wallet wallet)
            {
            }


            @Override
            public void onWalletChanged(Wallet wallet)
            {
            }

            @Override
            public void onScriptsAdded(Wallet wallet, List<Script> scripts)
            {
            }

            @Override
            public void onKeysAdded(List<ECKey> keys)
            {

            }
        });
    }

    private void fillData(Trade trade)
    {
        currentTrade = trade;
        Transaction transaction = trade.getDepositTransaction();
        if (transaction == null)
        {
            trade.depositTxChangedProperty().addListener((observableValue, aBoolean, aBoolean2) -> updateTx(trade.getDepositTransaction()));
        }
        else
        {
            updateTx(trade.getDepositTransaction());
        }

        // back details
        if (trade.getContract() != null)
        {
            setBankData(trade);
        }
        else
        {
            trade.contractChangedProperty().addListener((observableValue, aBoolean, aBoolean2) -> setBankData(trade));
        }

        // state
        trade.stateChangedProperty().addListener((observableValue, aString, aString2) -> setState(trade));
    }

    private void setState(Trade trade)
    {
        if (trade.getState() == Trade.State.COMPLETED)
        {
            Transaction transaction = trade.getPayoutTransaction();

            confidenceDisplay.destroy();
            confidenceDisplay = new ConfidenceDisplay(walletFacade.getWallet(), confirmationLabel, transaction, progressIndicator);

            txTextField.setText(transaction.getHashAsString());

            txHeaderLabel.setText("Payout transaction");
            txTitleLabel.setText("Payout transaction ID:");

            bankAccountDetailsHeaderLabel.setText("Summary");
            bankAccountTypeTitleLabel.setText("You have bought:");
            holderNameTitleLabel.setText("You have payed (" + trade.getOffer().getCurrency() + "):");
            primaryBankAccountIDTitleLabel.setText("Total fees (offer fee + tx fee):");
            secondaryBankAccountIDTitleLabel.setText("Refunded collateral:");

            bankAccountTypeTextField.setText(BitSquareFormatter.formatCoinToBtcWithCode(trade.getTradeAmount()));
            holderNameTextField.setText(BitSquareFormatter.formatVolume(trade.getTradeVolume()));
            primaryBankAccountIDTextField.setText(BitSquareFormatter.formatCoinToBtcWithCode(FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE)));
            secondaryBankAccountIDTextField.setText(BitSquareFormatter.formatCoinToBtcWithCode(trade.getCollateralAmount()));

            holderNameCopyIcon.setVisible(false);
            primaryBankAccountIDCopyIcon.setVisible(false);
            secondaryBankAccountIDCopyIcon.setVisible(false);

            bankTransferInitedButton.setVisible(false);

            AWTSystemTray.unSetAlert();
        }
    }

    private void setBankData(Trade trade)
    {
        BankAccount bankAccount = trade.getContract().getTakerBankAccount();
        bankAccountTypeTextField.setText(bankAccount.getBankAccountType().toString());
        holderNameTextField.setText(bankAccount.getAccountHolderName());
        primaryBankAccountIDTextField.setText(bankAccount.getAccountPrimaryID());
        secondaryBankAccountIDTextField.setText(bankAccount.getAccountSecondaryID());
    }

    private void initCopyIcons()
    {
        AwesomeDude.setIcon(txIDCopyIcon, AwesomeIcon.COPY);
        txIDCopyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(txTextField.getText());
            clipboard.setContent(content);
        });

        AwesomeDude.setIcon(holderNameCopyIcon, AwesomeIcon.COPY);
        holderNameCopyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(holderNameTextField.getText());
            clipboard.setContent(content);
        });

        AwesomeDude.setIcon(primaryBankAccountIDCopyIcon, AwesomeIcon.COPY);
        primaryBankAccountIDCopyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(primaryBankAccountIDTextField.getText());
            clipboard.setContent(content);
        });

        AwesomeDude.setIcon(secondaryBankAccountIDCopyIcon, AwesomeIcon.COPY);
        secondaryBankAccountIDCopyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(secondaryBankAccountIDTextField.getText());
            clipboard.setContent(content);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setCountryColumnCellFactory()
    {
        countryColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        countryColumn.setCellFactory(new Callback<TableColumn<String, PendingTradesListItem>, TableCell<String, PendingTradesListItem>>()
        {
            @Override
            public TableCell<String, PendingTradesListItem> call(TableColumn<String, PendingTradesListItem> directionColumn)
            {
                return new TableCell<String, PendingTradesListItem>()
                {
                    final HBox hBox = new HBox();

                    {
                        hBox.setSpacing(3);
                        hBox.setAlignment(Pos.CENTER);
                        setGraphic(hBox);
                    }

                    @Override
                    public void updateItem(final PendingTradesListItem tradesTableItem, boolean empty)
                    {
                        super.updateItem(tradesTableItem, empty);

                        hBox.getChildren().clear();
                        if (tradesTableItem != null)
                        {
                            Country country = tradesTableItem.getTrade().getOffer().getBankAccountCountry();
                            try
                            {
                                hBox.getChildren().add(Icons.getIconImageView("/images/countries/" + country.getCode().toLowerCase() + ".png"));

                            } catch (Exception e)
                            {
                                log.warn("Country icon not found: " + "/images/countries/" + country.getCode().toLowerCase() + ".png country name: " + country.getName());
                            }
                            Tooltip.install(this, new Tooltip(country.getName()));
                        }
                    }
                };
            }
        });
    }

    private void setBankAccountTypeColumnCellFactory()
    {
        bankAccountTypeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        bankAccountTypeColumn.setCellFactory(new Callback<TableColumn<String, PendingTradesListItem>, TableCell<String, PendingTradesListItem>>()
        {
            @Override
            public TableCell<String, PendingTradesListItem> call(TableColumn<String, PendingTradesListItem> directionColumn)
            {
                return new TableCell<String, PendingTradesListItem>()
                {
                    @Override
                    public void updateItem(final PendingTradesListItem tradesTableItem, boolean empty)
                    {
                        super.updateItem(tradesTableItem, empty);

                        if (tradesTableItem != null)
                        {
                            BankAccountType bankAccountType = tradesTableItem.getTrade().getOffer().getBankAccountType();
                            setText(Localisation.get(bankAccountType.toString()));
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
        directionColumn.setCellFactory(new Callback<TableColumn<String, PendingTradesListItem>, TableCell<String, PendingTradesListItem>>()
        {
            @Override
            public TableCell<String, PendingTradesListItem> call(TableColumn<String, PendingTradesListItem> directionColumn)
            {
                return new TableCell<String, PendingTradesListItem>()
                {
                    final ImageView iconView = new ImageView();
                    final Button button = new Button();

                    {
                        button.setGraphic(iconView);
                        button.setMinWidth(70);
                    }

                    @Override
                    public void updateItem(final PendingTradesListItem tradesTableItem, boolean empty)
                    {
                        super.updateItem(tradesTableItem, empty);

                        if (tradesTableItem != null)
                        {
                            String title;
                            Image icon;
                            Offer offer = tradesTableItem.getTrade().getOffer();

                            if (offer.getDirection() == Direction.SELL)
                            {
                                icon = buyIcon;
                                title = BitSquareFormatter.formatDirection(Direction.BUY, true);
                            }
                            else
                            {
                                icon = sellIcon;
                                title = BitSquareFormatter.formatDirection(Direction.SELL, true);
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
        selectColumn.setCellFactory(new Callback<TableColumn<String, PendingTradesListItem>, TableCell<String, PendingTradesListItem>>()
        {
            @Override
            public TableCell<String, PendingTradesListItem> call(TableColumn<String, PendingTradesListItem> directionColumn)
            {
                return new TableCell<String, PendingTradesListItem>()
                {
                    final Button button = new Button("Select");

                    @Override
                    public void updateItem(final PendingTradesListItem tradesTableItem, boolean empty)
                    {
                        super.updateItem(tradesTableItem, empty);

                        if (tradesTableItem != null)
                        {
                            button.setOnAction(event -> showTradeDetails(tradesTableItem));
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

