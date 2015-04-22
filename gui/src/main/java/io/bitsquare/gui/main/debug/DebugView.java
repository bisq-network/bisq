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
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.common.view.InitializableView;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityProtocol;
import io.bitsquare.trade.protocol.availability.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.availability.tasks.ProcessOfferAvailabilityResponse;
import io.bitsquare.trade.protocol.availability.tasks.SendOfferAvailabilityRequest;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferProtocol;
import io.bitsquare.trade.protocol.placeoffer.tasks.AddOfferToRemoteOfferBook;
import io.bitsquare.trade.protocol.placeoffer.tasks.BroadcastCreateOfferFeeTx;
import io.bitsquare.trade.protocol.placeoffer.tasks.CreateOfferFeeTx;
import io.bitsquare.trade.protocol.placeoffer.tasks.ValidateOffer;
import io.bitsquare.trade.protocol.trade.BuyerAsOffererProtocol;
import io.bitsquare.trade.protocol.trade.SellerAsTakerProtocol;
import io.bitsquare.trade.protocol.trade.tasks.buyer.CreateDepositTxInputs;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessDepositTxInputsRequest;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessFinalizePayoutTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessPublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendPayDepositRequest;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendPayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SignAndFinalizePayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SignAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.buyer.VerifyAndSignContract;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakerAccount;
import io.bitsquare.trade.protocol.trade.tasks.seller.CommitDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.seller.CreateAndSignContract;
import io.bitsquare.trade.protocol.trade.tasks.seller.CreateAndSignDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessPayDepositRequest;
import io.bitsquare.trade.protocol.trade.tasks.seller.ProcessPayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendDepositTxInputsRequest;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendFinalizePayoutTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.seller.SendPublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.seller.SignPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.CommitPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;
import io.bitsquare.trade.protocol.trade.tasks.taker.BroadcastTakeOfferFeeTx;
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

    @Inject
    public DebugView() {
    }

    @Override
    public void initialize() {
        final ObservableList<Class> items = FXCollections.observableArrayList(Arrays.asList(
                        /*---- Protocol ----*/
                        OfferAvailabilityProtocol.class,
                        GetPeerAddress.class,
                        SendOfferAvailabilityRequest.class,
                        ProcessOfferAvailabilityResponse.class,
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
                        ProcessDepositTxInputsRequest.class,
                        CreateDepositTxInputs.class,
                        SendPayDepositRequest.class,

                        ProcessPublishDepositTxRequest.class,
                        VerifyTakerAccount.class,
                        VerifyAndSignContract.class,
                        SignAndPublishDepositTx.class,
                        SendDepositTxPublishedMessage.class,

                        VerifyTakeOfferFeePayment.class,
                        SendFiatTransferStartedMessage.class,

                        ProcessFinalizePayoutTxRequest.class,
                        SignAndFinalizePayoutTx.class,
                        CommitPayoutTx.class,
                        SendPayoutTxFinalizedMessage.class,
                        SetupPayoutTxLockTimeReachedListener.class,
                        Boolean.class, /* used as seperator*/
                        

                        /*---- Protocol ----*/
                        SellerAsTakerProtocol.class,
                        CreateTakeOfferFeeTx.class,
                        BroadcastTakeOfferFeeTx.class,
                        SendDepositTxInputsRequest.class,

                        ProcessPayDepositRequest.class,
                        VerifyOffererAccount.class,
                        CreateAndSignContract.class,
                        CreateAndSignDepositTx.class,
                        SendPublishDepositTxRequest.class,

                        ProcessDepositTxPublishedMessage.class,
                        CommitDepositTx.class,

                        ProcessFiatTransferStartedMessage.class,

                        VerifyOfferFeePayment.class,
                        SignPayoutTx.class,
                        SendFinalizePayoutTxRequest.class,

                        ProcessPayoutTxFinalizedMessage.class,
                        CommitPayoutTx.class,
                        SetupPayoutTxLockTimeReachedListener.class
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
            Task.taskToIntercept = item;
        }
    }
}

