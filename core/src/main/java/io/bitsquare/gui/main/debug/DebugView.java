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
import io.bitsquare.trade.protocol.trade.offerer.BuyerAsOffererProtocol;
import io.bitsquare.trade.protocol.trade.offerer.tasks.CreateOffererDepositTxInputs;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessPayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.ProcessRequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.RequestDepositPayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendBankTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SendDepositTxIdToTaker;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SetupListenerForBlockChainConfirmation;
import io.bitsquare.trade.protocol.trade.offerer.tasks.SignAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.CreateAndSignPayoutTx;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyAndSignContract;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.offerer.tasks.VerifyTakerAccount;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerProtocol;
import io.bitsquare.trade.protocol.trade.taker.tasks.CreateAndSignContract;
import io.bitsquare.trade.protocol.trade.taker.tasks.CreateTakeOfferFeeTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessBankTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.ProcessRequestDepositPaymentMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendPayoutTxToOfferer;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.SendSignedTakerDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.SignAndPublishPayoutTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCommitDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerCreatesAndSignsDepositTx;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOfferFeePayment;
import io.bitsquare.trade.protocol.trade.taker.tasks.VerifyOffererAccount;

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
                        CreateOffererDepositTxInputs.class,
                        RequestDepositPayment.class,

                        ProcessRequestOffererPublishDepositTxMessage.class,
                        VerifyTakerAccount.class,
                        VerifyAndSignContract.class,
                        SignAndPublishDepositTx.class,
                        SetupListenerForBlockChainConfirmation.class,
                        SendDepositTxIdToTaker.class,

                        CreateAndSignPayoutTx.class,
                        VerifyTakeOfferFeePayment.class,
                        SendBankTransferStartedMessage.class,

                        ProcessPayoutTxPublishedMessage.class,
                        Boolean.class, /* used as seperator*/
                        

                        /*---- Protocol ----*/
                        SellerAsTakerProtocol.class,
                        CreateTakeOfferFeeTx.class,
                        SendRequestDepositTxInputsMessage.class,

                        ProcessRequestDepositPaymentMessage.class,
                        VerifyOffererAccount.class,
                        CreateAndSignContract.class,
                        TakerCreatesAndSignsDepositTx.class,
                        SendSignedTakerDepositTx.class,

                        ProcessDepositTxPublishedMessage.class,
                        TakerCommitDepositTx.class,

                        ProcessBankTransferStartedMessage.class,

                        SignAndPublishPayoutTx.class,
                        VerifyOfferFeePayment.class,
                        SendPayoutTxToOfferer.class
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

