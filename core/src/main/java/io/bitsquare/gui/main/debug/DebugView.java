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

package io.bitsquare.gui.main.debug;

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.common.viewfx.view.InitializableView;
import io.bitsquare.trade.protocol.availability.CheckOfferAvailabilityProtocol;
import io.bitsquare.trade.protocol.availability.tasks.ProcessReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.availability.tasks.RequestIsOfferAvailable;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferProtocol;
import io.bitsquare.trade.protocol.placeoffer.tasks.AddOfferToRemoteOfferBook;
import io.bitsquare.trade.protocol.placeoffer.tasks.BroadcastCreateOfferFeeTx;
import io.bitsquare.trade.protocol.placeoffer.tasks.CreateOfferFeeTx;
import io.bitsquare.trade.protocol.placeoffer.tasks.ValidateOffer;
import io.bitsquare.trade.protocol.trade.BuyerAsOffererProtocol;
import io.bitsquare.trade.protocol.trade.SellerAsTakerProtocol;
import io.bitsquare.trade.protocol.trade.tasks.buyer.BuyerCreatesAndSignPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.buyer.BuyerCreatesDepositTxInputs;
import io.bitsquare.trade.protocol.trade.tasks.buyer.BuyerProcessPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.BuyerProcessRequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.BuyerSendsDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.BuyerSendsFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.BuyerSendsRequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.BuyerSignsAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakerAccount;
import io.bitsquare.trade.protocol.trade.tasks.seller.SellerCommitDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.seller.SellerCreatesAndSignsDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.seller.SellerProcessDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SellerProcessFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SellerProcessRequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SellerSendsPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SellerSignsAndPublishPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.tasks.taker.CreateTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.tasks.taker.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.tasks.taker.VerifyOffererAccount;

import java.util.Arrays;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;

@FxmlView
public class DebugView extends InitializableView {


    @FXML ComboBox<Class> taskComboBox;
    @FXML CheckBox interceptBeforeCheckBox;

    @Inject
    public DebugView() {
    }

    @Override
    public void initialize() {
        interceptBeforeCheckBox.setSelected(true);

        final ObservableList<Class> items = FXCollections.observableArrayList(Arrays.asList(
                        /*---- Protocol ----*/
                        CheckOfferAvailabilityProtocol.class,
                        io.bitsquare.trade.protocol.availability.tasks.GetPeerAddress.class,
                        RequestIsOfferAvailable.class,
                        ProcessReportOfferAvailabilityMessage.class,
                        Boolean.class, /* used as seperator*/

                        
                        /*---- Protocol ----*/
                        PlaceOfferProtocol.class,
                        ValidateOffer.class,
                        CreateOfferFeeTx.class,
                        AddOfferToRemoteOfferBook.class,
                        BroadcastCreateOfferFeeTx.class,
                        Boolean.class, /* used as seperator*/

                        
                        /*---- Protocol ----*/
                        BuyerAsOffererProtocol.class,
                        ProcessRequestDepositTxInputsMessage.class,
                        BuyerCreatesDepositTxInputs.class,
                        BuyerSendsRequestPayDepositMessage.class,

                        BuyerProcessRequestPublishDepositTxMessage.class,
                        VerifyTakerAccount.class,
                        BuyerSignsAndPublishDepositTx.class,
                        BuyerSendsDepositTxPublishedMessage.class,

                        BuyerCreatesAndSignPayoutTx.class,
                        VerifyTakeOfferFeePayment.class,
                        BuyerSendsFiatTransferStartedMessage.class,

                        BuyerProcessPayoutTxPublishedMessage.class,
                        Boolean.class, /* used as seperator*/
                        

                        /*---- Protocol ----*/
                        SellerAsTakerProtocol.class,
                        CreateTakeOfferFeeTx.class,
                        SendRequestDepositTxInputsMessage.class,

                        SellerProcessRequestPayDepositMessage.class,
                        VerifyOffererAccount.class,
                        SellerCreatesAndSignsDepositTx.class,

                        SellerProcessDepositTxPublishedMessage.class,
                        SellerCommitDepositTx.class,

                        SellerProcessFiatTransferStartedMessage.class,

                        SellerSignsAndPublishPayoutTx.class,
                        VerifyOfferFeePayment.class,
                        SellerSendsPayoutTxPublishedMessage.class
                )
        );


        taskComboBox.setVisibleRowCount(items.size());
        taskComboBox.setItems(items);
        taskComboBox.setConverter(new StringConverter<Class>() {
            @Override
            public String toString(Class item) {
                if (item.getSimpleName().contains("Protocol"))
                    return "--- " + item.getSimpleName() + " ---";
                else if (item.getSimpleName().contains("Boolean"))
                    return "";
                else
                    return item.getSimpleName();
            }

            @Override
            public Class fromString(String s) {
                return null;
            }
        });
    }

    @FXML
    void onSelectTask() {
        Class item = taskComboBox.getSelectionModel().getSelectedItem();
        if (!item.getSimpleName().contains("Protocol")) {
            if (interceptBeforeCheckBox.isSelected()) {
                Task.taskToInterceptBeforeRun = item;
                Task.taskToInterceptAfterRun = null;
            }
            else {
                Task.taskToInterceptAfterRun = item;
                Task.taskToInterceptBeforeRun = null;
            }
        }
    }

    @FXML
    void onCheckBoxChanged() {
        onSelectTask();
    }
}

