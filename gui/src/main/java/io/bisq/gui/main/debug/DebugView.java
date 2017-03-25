/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.debug;

import io.bisq.common.taskrunner.Task;
import io.bisq.core.offer.availability.OfferAvailabilityProtocol;
import io.bisq.core.offer.availability.tasks.ProcessOfferAvailabilityResponse;
import io.bisq.core.offer.availability.tasks.SendOfferAvailabilityRequest;
import io.bisq.core.offer.placeoffer.PlaceOfferProtocol;
import io.bisq.core.offer.placeoffer.tasks.AddOfferToRemoteOfferBook;
import io.bisq.core.offer.placeoffer.tasks.BroadcastCreateOfferFeeTx;
import io.bisq.core.offer.placeoffer.tasks.CreateOfferFeeTx;
import io.bisq.core.offer.placeoffer.tasks.ValidateOffer;
import io.bisq.core.trade.protocol.BuyerAsOffererProtocol;
import io.bisq.core.trade.protocol.BuyerAsTakerProtocol;
import io.bisq.core.trade.protocol.SellerAsOffererProtocol;
import io.bisq.core.trade.protocol.SellerAsTakerProtocol;
import io.bisq.core.trade.protocol.tasks.buyer.*;
import io.bisq.core.trade.protocol.tasks.offerer.*;
import io.bisq.core.trade.protocol.tasks.seller.*;
import io.bisq.core.trade.protocol.tasks.shared.BroadcastPayoutTx;
import io.bisq.core.trade.protocol.tasks.taker.*;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.common.view.InitializableView;
import io.bisq.gui.components.TitledGroupBg;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.util.StringConverter;

import javax.inject.Inject;
import java.util.Arrays;

@FxmlView
public class DebugView extends InitializableView {


    @FXML
    TitledGroupBg titledGroupBg;
    @FXML
    Label label;
    @FXML
    ComboBox<Class> taskComboBox1, taskComboBox2;

    @Inject
    public DebugView() {
    }

    @Override
    public void initialize() {
        titledGroupBg.setText("Intercept task");
        label.setText("Select Task:");
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
                        OffererAsBuyerCreatesAndSignsDepositTx.class,
                        LoadTakeOfferFeeTx.class,
                        SetupDepositBalanceListener.class,
                        SendPublishDepositTxRequest.class,

                        ProcessDepositTxPublishedMessage.class,

                        VerifyTakerFeePayment.class,
                        SendFiatTransferStartedMessage.class,

                        ProcessFinalizePayoutTxRequest.class,
                        BuyerAsTakerSignAndFinalizePayoutTx.class,
                        SendPayoutTxFinalizedMessage.class,
                        BroadcastPayoutTx.class,
                        Boolean.class, /* used as separator*/
                        

                        /*---- Protocol ----*/
                        SellerAsTakerProtocol.class,
                        SelectArbitrator.class,
                        CreateTakeOfferFeeTx.class,
                        BroadcastTakeOfferFeeTx.class,
                        TakerAsSellerCreatesDepositTxInputs.class,
                        SendPayDepositRequest.class,

                        ProcessPublishDepositTxRequest.class,
                        VerifyMakerAccount.class,
                        VerifyAndSignContract.class,
                        SignAndPublishDepositTxAsSeller.class,
                        SendDepositTxPublishedMessage.class,

                        ProcessFiatTransferStartedMessage.class,

                        VerifyMakerFeePayment.class,
                        SignPayoutTx.class,
                        SellerAsOffererSendFinalizePayoutTxRequest.class,

                        ProcessPayoutTxFinalizedMessage.class,
                        BroadcastPayoutTx.class,
                        Boolean.class /* used as separator*/
                )
        );
        final ObservableList<Class> items2 = FXCollections.observableArrayList(Arrays.asList(
                        /*---- Protocol ----*/
                        BuyerAsTakerProtocol.class,
                        SelectArbitrator.class,
                        CreateTakeOfferFeeTx.class,
                        BroadcastTakeOfferFeeTx.class,
                        TakerAsSellerCreatesDepositTxInputs.class,
                        SendPayDepositRequest.class,

                        ProcessPublishDepositTxRequest.class,
                        VerifyMakerAccount.class,
                        VerifyAndSignContract.class,
                        SignAndPublishDepositTxAsSeller.class,
                        SendDepositTxPublishedMessage.class,

                        VerifyMakerFeePayment.class,
                        SignPayoutTx.class,
                        SendFiatTransferStartedMessage.class,

                        ProcessFinalizePayoutTxRequest.class,
                        BuyerAsTakerSignAndFinalizePayoutTx.class,
                        SendPayoutTxFinalizedMessage.class,
                        BroadcastPayoutTx.class,
                        Boolean.class, /* used as separator*/
                        
                        
                         /*---- Protocol ----*/
                        SellerAsOffererProtocol.class,
                        ProcessPayDepositRequest.class,
                        VerifyArbitrationSelection.class,
                        VerifyTakerAccount.class,
                        CreateAndSignContract.class,
                        OffererAsBuyerCreatesAndSignsDepositTx.class,
                        SetupDepositBalanceListener.class,
                        SendPublishDepositTxRequest.class,

                        ProcessDepositTxPublishedMessage.class,

                        ProcessFiatTransferStartedMessage.class,

                        VerifyTakerFeePayment.class,
                        SignPayoutTx.class,
                        SellerAsOffererSendFinalizePayoutTxRequest.class,

                        ProcessPayoutTxFinalizedMessage.class,
                        BroadcastPayoutTx.class,
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

