package io.bitsquare.gui.msg;

import com.google.inject.Inject;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.MessageListener;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Callback;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.ResourceBundle;

public class MsgController implements Initializable, ChildController, MessageListener
{
    private static final Logger log = LoggerFactory.getLogger(MsgController.class);

    private MessageFacade messageFacade;
    private String selectedCurrency;
    private ObservableList<OfferListItem> offerList = FXCollections.observableArrayList();
    private int selectedIndex;
    private String myID, otherID;
    private boolean pingPending;

    @FXML
    public ComboBox currencyComboBox;
    @FXML
    public Button sendButton;
    @FXML
    public TextArea chatTextArea;
    @FXML
    public TextField chatInputField, peerIDTextField, currencyTextField, offerDataTextField;
    @FXML
    public TableView offerTable;
    @FXML
    public TableColumn<String, OfferListItem> connectToPeerColumn, removeOfferColumn, offerColumn;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MsgController(MessageFacade messageFacade)
    {
        this.messageFacade = messageFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        myID = WalletFacade.WALLET_PREFIX;
        otherID = WalletFacade.WALLET_PREFIX.equals("taker") ? "offerer" : "taker";

        messageFacade.addMessageListener(this);

        peerIDTextField.setText(myID);
        currencyTextField.setText("EUR");
        offerDataTextField.setText(myID + " serialized offer object");

        selectedCurrency = currencyTextField.getText();

        currencyComboBox.setItems(FXCollections.observableArrayList(new ArrayList<>(Arrays.asList("EUR", "USD", "CHF"))));
        currencyComboBox.getSelectionModel().select(0);

        setupConnectToPeerOfferColumn();
        setupRemoveOfferColumn();
        offerTable.setItems(offerList);
        offerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: MessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Object message)
    {
        sendButton.setDisable(!messageFacade.isOtherPeerDefined());

        if (message instanceof String)
            chatTextArea.appendText("\n" + otherID + ": " + message);
    }

    @Override
    public void onPing()
    {
        sendChatMsg(MessageFacade.PONG);
    }

    @Override
    public void onOfferPublished(boolean success)
    {
        if (success)
            getOffers();
        else
            log.warn("onOfferPublished returned false");
    }

    @Override
    public void onOffersReceived(Map<Number160, Data> dataMap, boolean success)
    {
        if (success && dataMap != null)
        {
            offerList.clear();
            for (Data offerData : dataMap.values())
            {
                try
                {
                    Object offerDataObject = offerData.getObject();
                    if (offerDataObject instanceof OfferListItem && offerDataObject != null)
                        offerList.add((OfferListItem) offerDataObject);
                } catch (ClassNotFoundException | IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        else
        {
            offerList.clear();
        }
    }


    @Override
    public void onOfferRemoved(boolean success)
    {
        if (success)
            getOffers();
        else
            log.warn("onOfferRemoved failed");
    }

    @Override
    public void onResponseFromSend(Object response)
    {
        String msg = (response instanceof String) ? (String) response : null;
        if (msg != null)
        {
            chatTextArea.appendText("\n" + otherID + ": " + msg);
            offerTable.getSelectionModel().select(selectedIndex);
        }
    }

    @Override
    public void onSendFailed()
    {
        offerTable.getSelectionModel().clearSelection();
    }

    @Override
    public void onPeerFound()
    {
        sendButton.setDisable(!messageFacade.isOtherPeerDefined());
        if (pingPending)
            sendChatMsg(MessageFacade.PING);
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
        messageFacade.removeMessageListener(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // GUI Event handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void publishOffer(ActionEvent actionEvent)
    {
        OfferListItem offerListItem = new OfferListItem(offerDataTextField.getText(), messageFacade.getPubKeyAsHex(), currencyTextField.getText());
        try
        {
            messageFacade.publishOffer(currencyTextField.getText(), offerListItem);
        } catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @FXML
    public void selectCurrency(ActionEvent actionEvent)
    {
        selectedCurrency = currencyComboBox.getSelectionModel().getSelectedItem().toString();
        getOffers();
    }

    @FXML
    public void sendChatMsg(ActionEvent actionEvent)
    {
        sendChatMsg(chatInputField.getText());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void inviteForChat(OfferListItem item, int index)
    {
        selectedIndex = index;
        messageFacade.findPeer(item.getPubKey());
        pingPending = true;

    }

    private void sendChatMsg(String msg)
    {
        messageFacade.sendMessage(msg);

        chatTextArea.appendText("\n" + myID + ": " + msg);
        chatInputField.setText("");
    }

    private void getOffers()
    {
        messageFacade.getOffers(selectedCurrency);
    }

    private void removeOffer(OfferListItem offer)
    {
        try
        {
            messageFacade.removeOffer(currencyTextField.getText(), offer);
        } catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupRemoveOfferColumn()
    {
        removeOfferColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        removeOfferColumn.setCellFactory(new Callback<TableColumn<String, OfferListItem>, TableCell<String, OfferListItem>>()
        {
            @Override
            public TableCell<String, OfferListItem> call(TableColumn<String, OfferListItem> directionColumn)
            {
                return new TableCell<String, OfferListItem>()
                {
                    final Button button = new Button();

                    {
                        button.setMinWidth(70);
                    }

                    @Override
                    public void updateItem(final OfferListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null)
                        {
                            button.setText("Remove");
                            setGraphic(button);

                            button.setOnAction(event -> removeOffer(item));
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

    private void setupConnectToPeerOfferColumn()
    {
        connectToPeerColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        connectToPeerColumn.setCellFactory(new Callback<TableColumn<String, OfferListItem>, TableCell<String, OfferListItem>>()
        {
            @Override
            public TableCell<String, OfferListItem> call(TableColumn<String, OfferListItem> directionColumn)
            {
                return new TableCell<String, OfferListItem>()
                {
                    final Button button = new Button();

                    {
                        button.setMinWidth(70);
                    }

                    @Override
                    public void updateItem(OfferListItem item, boolean empty)
                    {
                        super.updateItem(item, empty);

                        if (item != null)
                        {
                            button.setText("Chat");
                            setGraphic(button);


                            button.setOnAction(event -> inviteForChat(item, getIndex()));
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

