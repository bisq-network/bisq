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
import io.bitsquare.trade.protocol.availability.tasks.ProcessOfferAvailabilityResponse;
import io.bitsquare.trade.protocol.availability.tasks.SendOfferAvailabilityRequest;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferProtocol;
import io.bitsquare.trade.protocol.placeoffer.tasks.AddOfferToRemoteOfferBook;
import io.bitsquare.trade.protocol.placeoffer.tasks.BroadcastCreateOfferFeeTx;
import io.bitsquare.trade.protocol.placeoffer.tasks.CreateOfferFeeTx;
import io.bitsquare.trade.protocol.placeoffer.tasks.ValidateOffer;
import io.bitsquare.trade.protocol.trade.BuyerAsOffererProtocol;
import io.bitsquare.trade.protocol.trade.BuyerAsTakerProtocol;
import io.bitsquare.trade.protocol.trade.SellerAsOffererProtocol;
import io.bitsquare.trade.protocol.trade.SellerAsTakerProtocol;
import io.bitsquare.trade.protocol.trade.tasks.buyer.*;
import io.bitsquare.trade.protocol.trade.tasks.offerer.*;
import io.bitsquare.trade.protocol.trade.tasks.seller.*;
import io.bitsquare.trade.protocol.trade.tasks.shared.BroadcastAfterLockTime;
import io.bitsquare.trade.protocol.trade.tasks.shared.InitWaitPeriodForOpenDispute;
import io.bitsquare.trade.protocol.trade.tasks.taker.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import javax.inject.Inject;
import java.util.Arrays;

@FxmlView
public class DebugView extends InitializableView {


    @FXML
    ComboBox<Class> taskComboBox1, taskComboBox2;

    @Inject
    public DebugView() {
    }

    @Override
    public void initialize() {
        final ObservableList<Class> items1 = FXCollections.observableArrayList(Arrays.asList(
                        /*---- Protocol ----*/
                        OfferAvailabilityProtocol.class,
                        SendOfferAvailabilityRequest.class,
                        ProcessOfferAvailabilityResponse.class,
                        Boolean.class, /* used as separator*/

                        
                        /*---- Protocol ----*/
                        PlaceOfferProtocol.class,
                        ValidateOffer.class,
                        CreateOfferFeeTx.class,
                        AddOfferToRemoteOfferBook.class,
                        BroadcastCreateOfferFeeTx.class,
                        Boolean.class, /* used as separator*/

                        
                        /*---- Protocol ----*/
                        BuyerAsOffererProtocol.class,
                        ProcessPayDepositRequest.class,
                        VerifyArbitrationSelection.class,
                        VerifyTakerAccount.class,
                        CreateAndSignContract.class,
                        CreateAndSignDepositTxAsBuyer.class,
                        LoadTakeOfferFeeTx.class,
                        InitWaitPeriodForOpenDispute.class,
                        SetupDepositBalanceListener.class,
                        SendPublishDepositTxRequest.class,

                        ProcessDepositTxPublishedMessage.class,

                        VerifyTakeOfferFeePayment.class,
                        SendFiatTransferStartedMessage.class,

                        ProcessFinalizePayoutTxRequest.class,
                        SignAndFinalizePayoutTx.class,
                        SendPayoutTxFinalizedMessage.class,
                        BroadcastAfterLockTime.class,
                        Boolean.class, /* used as separator*/
                        

                        /*---- Protocol ----*/
                        SellerAsTakerProtocol.class,
                        SelectArbitrator.class,
                        CreateTakeOfferFeeTx.class,
                        BroadcastTakeOfferFeeTx.class,
                        CreateDepositTxInputsAsSeller.class,
                        SendPayDepositRequest.class,

                        ProcessPublishDepositTxRequest.class,
                        VerifyOffererAccount.class,
                        VerifyAndSignContract.class,
                        SignAndPublishDepositTxAsSeller.class,
                        SendDepositTxPublishedMessage.class,

                        ProcessFiatTransferStartedMessage.class,

                        VerifyOfferFeePayment.class,
                        SignPayoutTx.class,
                        SendFinalizePayoutTxRequest.class,

                        ProcessPayoutTxFinalizedMessage.class,
                        BroadcastAfterLockTime.class,
                        Boolean.class /* used as separator*/
                )
        );
        final ObservableList<Class> items2 = FXCollections.observableArrayList(Arrays.asList(
                        /*---- Protocol ----*/
                        BuyerAsTakerProtocol.class,
                        SelectArbitrator.class,
                        CreateTakeOfferFeeTx.class,
                        BroadcastTakeOfferFeeTx.class,
                        CreateDepositTxInputsAsSeller.class,
                        SendPayDepositRequest.class,

                        ProcessPublishDepositTxRequest.class,
                        VerifyOffererAccount.class,
                        VerifyAndSignContract.class,
                        SignAndPublishDepositTxAsSeller.class,
                        SendDepositTxPublishedMessage.class,

                        VerifyOfferFeePayment.class,
                        SignPayoutTx.class,
                        SendFiatTransferStartedMessage.class,

                        ProcessFinalizePayoutTxRequest.class,
                        SignAndFinalizePayoutTx.class,
                        SendPayoutTxFinalizedMessage.class,
                        BroadcastAfterLockTime.class,
                        Boolean.class, /* used as separator*/
                        
                        
                         /*---- Protocol ----*/
                        SellerAsOffererProtocol.class,
                        ProcessPayDepositRequest.class,
                        VerifyArbitrationSelection.class,
                        VerifyTakerAccount.class,
                        InitWaitPeriodForOpenDispute.class,
                        CreateAndSignContract.class,
                        CreateAndSignDepositTxAsBuyer.class,
                        SetupDepositBalanceListener.class,
                        SendPublishDepositTxRequest.class,

                        ProcessDepositTxPublishedMessage.class,

                        ProcessFiatTransferStartedMessage.class,

                        VerifyTakeOfferFeePayment.class,
                        SignPayoutTx.class,
                        SendFinalizePayoutTxRequest.class,

                        ProcessPayoutTxFinalizedMessage.class,
                        BroadcastAfterLockTime.class,
                        Boolean.class /* used as separator*/
                )
        );

        taskComboBox1.setVisibleRowCount(items1.size());
        taskComboBox1.setItems(items1);
        taskComboBox1.setConverter(new StringConverter<Class>() {
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


        taskComboBox2.setVisibleRowCount(items2.size());
        taskComboBox2.setItems(items2);
        taskComboBox2.setConverter(new StringConverter<Class>() {
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
    void onSelectTask1() {
        Class item = taskComboBox1.getSelectionModel().getSelectedItem();
        if (!item.getSimpleName().contains("Protocol")) {
            Task.taskToIntercept = item;
        }
    }

    @FXML
    void onSelectTask2() {
        Class item = taskComboBox2.getSelectionModel().getSelectedItem();
        if (!item.getSimpleName().contains("Protocol")) {
            Task.taskToIntercept = item;
        }
    }


}

