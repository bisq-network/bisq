/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.debug;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.InitializableView;
import bisq.desktop.components.TitledGroupBg;

import bisq.core.offer.availability.tasks.ProcessOfferAvailabilityResponse;
import bisq.core.offer.availability.tasks.SendOfferAvailabilityRequest;
import bisq.core.offer.placeoffer.bisq_v1.tasks.AddToOfferBook;
import bisq.core.offer.placeoffer.bisq_v1.tasks.CreateMakerFeeTx;
import bisq.core.offer.placeoffer.bisq_v1.tasks.ValidateOffer;
import bisq.core.trade.protocol.bisq_v1.tasks.ApplyFilter;
import bisq.core.trade.protocol.bisq_v1.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerProcessDelayedPayoutTxSignatureRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerProcessDepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerProcessPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSendCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSendsDelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSetupDepositTxListener;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSetupPayoutTxListener;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSignPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerSignsDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerVerifiesFinalDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer.BuyerVerifiesPreparedDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_maker.BuyerAsMakerSendsInputsForDepositTxResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_taker.BuyerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_taker.BuyerAsTakerSendsDepositTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_taker.BuyerAsTakerSignsDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerProcessesInputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerRemovesOpenOffer;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerSetsLockTime;
import bisq.core.trade.protocol.bisq_v1.tasks.maker.MakerVerifyTakerFeePayment;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerBroadcastPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerCreatesDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerFinalizesDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerProcessCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerProcessDelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerPublishesTradeStatistics;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSendDelayedPayoutTxSignatureRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSendPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSendsDepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSignAndFinalizePayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller.SellerSignsDelayedPayoutTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_maker.SellerAsMakerCreatesUnsignedDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_maker.SellerAsMakerFinalizesDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_maker.SellerAsMakerProcessDepositTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_maker.SellerAsMakerSendsInputsForDepositTxResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_taker.SellerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.bisq_v1.tasks.seller_as_taker.SellerAsTakerSignsDepositTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.CreateTakerFeeTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerProcessesInputsForDepositTxResponse;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerPublishFeeTx;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerSendInputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerVerifyAndSignContract;
import bisq.core.trade.protocol.bisq_v1.tasks.taker.TakerVerifyMakerFeePayment;

import bisq.common.taskrunner.Task;
import bisq.common.util.Tuple2;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.util.StringConverter;

import java.util.Arrays;

import static bisq.desktop.util.FormBuilder.addTopLabelComboBox;

// Not maintained anymore with new trade protocol, but leave it...If used needs to be adopted to current protocol.
@FxmlView
public class DebugView extends InitializableView<GridPane, Void> {

    @FXML
    TitledGroupBg titledGroupBg;
    private int rowIndex = 0;

    @Inject
    public DebugView() {
    }

    @Override
    public void initialize() {

        addGroup("OfferAvailabilityProtocol",
                FXCollections.observableArrayList(Arrays.asList(
                        SendOfferAvailabilityRequest.class,
                        ProcessOfferAvailabilityResponse.class)
                ));

        addGroup("PlaceOfferProtocol",
                FXCollections.observableArrayList(Arrays.asList(
                        ValidateOffer.class,
                        CreateMakerFeeTx.class,
                        AddToOfferBook.class)
                ));


        addGroup("SellerAsTakerProtocol",
                FXCollections.observableArrayList(Arrays.asList(
                        ApplyFilter.class,
                        TakerVerifyMakerFeePayment.class,
                        CreateTakerFeeTx.class,
                        SellerAsTakerCreatesDepositTxInputs.class,
                        TakerSendInputsForDepositTxRequest.class,

                        TakerProcessesInputsForDepositTxResponse.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class,
                        TakerVerifyAndSignContract.class,
                        TakerPublishFeeTx.class,
                        SellerAsTakerSignsDepositTx.class,
                        SellerCreatesDelayedPayoutTx.class,
                        SellerSendDelayedPayoutTxSignatureRequest.class,

                        SellerProcessDelayedPayoutTxSignatureResponse.class,
                        SellerSignsDelayedPayoutTx.class,
                        SellerFinalizesDelayedPayoutTx.class,
                        SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                        SellerPublishesDepositTx.class,
                        SellerPublishesTradeStatistics.class,

                        SellerProcessCounterCurrencyTransferStartedMessage.class,
                        ApplyFilter.class,
                        TakerVerifyMakerFeePayment.class,

                        ApplyFilter.class,
                        TakerVerifyMakerFeePayment.class,
                        SellerSignAndFinalizePayoutTx.class,
                        SellerBroadcastPayoutTx.class,
                        SellerSendPayoutTxPublishedMessage.class

                        )
                ));
        addGroup("BuyerAsMakerProtocol",
                FXCollections.observableArrayList(Arrays.asList(
                        MakerProcessesInputsForDepositTxRequest.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class,
                        MakerVerifyTakerFeePayment.class,
                        MakerSetsLockTime.class,
                        MakerCreateAndSignContract.class,
                        BuyerAsMakerCreatesAndSignsDepositTx.class,
                        BuyerSetupDepositTxListener.class,
                        BuyerAsMakerSendsInputsForDepositTxResponse.class,

                        BuyerProcessDelayedPayoutTxSignatureRequest.class,
                        MakerRemovesOpenOffer.class,
                        BuyerVerifiesPreparedDelayedPayoutTx.class,
                        BuyerSignsDelayedPayoutTx.class,
                        BuyerSendsDelayedPayoutTxSignatureResponse.class,

                        BuyerProcessDepositTxAndDelayedPayoutTxMessage.class,
                        BuyerVerifiesFinalDelayedPayoutTx.class,

                        ApplyFilter.class,
                        MakerVerifyTakerFeePayment.class,
                        BuyerSignPayoutTx.class,
                        BuyerSetupPayoutTxListener.class,
                        BuyerSendCounterCurrencyTransferStartedMessage.class,

                        BuyerProcessPayoutTxPublishedMessage.class
                        )
                ));


        addGroup("BuyerAsTakerProtocol",
                FXCollections.observableArrayList(Arrays.asList(
                        ApplyFilter.class,
                        TakerVerifyMakerFeePayment.class,
                        CreateTakerFeeTx.class,
                        BuyerAsTakerCreatesDepositTxInputs.class,
                        TakerSendInputsForDepositTxRequest.class,

                        TakerProcessesInputsForDepositTxResponse.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class,
                        TakerVerifyAndSignContract.class,
                        TakerPublishFeeTx.class,
                        BuyerAsTakerSignsDepositTx.class,
                        BuyerSetupDepositTxListener.class,
                        BuyerAsTakerSendsDepositTxMessage.class,

                        BuyerProcessDelayedPayoutTxSignatureRequest.class,
                        BuyerVerifiesPreparedDelayedPayoutTx.class,
                        BuyerSignsDelayedPayoutTx.class,
                        BuyerSendsDelayedPayoutTxSignatureResponse.class,

                        BuyerProcessDepositTxAndDelayedPayoutTxMessage.class,
                        BuyerVerifiesFinalDelayedPayoutTx.class,

                        ApplyFilter.class,
                        TakerVerifyMakerFeePayment.class,
                        BuyerSignPayoutTx.class,
                        BuyerSetupPayoutTxListener.class,
                        BuyerSendCounterCurrencyTransferStartedMessage.class,

                        BuyerProcessPayoutTxPublishedMessage.class)
                ));
        addGroup("SellerAsMakerProtocol",
                FXCollections.observableArrayList(Arrays.asList(
                        MakerProcessesInputsForDepositTxRequest.class,
                        ApplyFilter.class,
                        VerifyPeersAccountAgeWitness.class,
                        MakerVerifyTakerFeePayment.class,
                        MakerSetsLockTime.class,
                        MakerCreateAndSignContract.class,
                        SellerAsMakerCreatesUnsignedDepositTx.class,
                        SellerAsMakerSendsInputsForDepositTxResponse.class,

                        SellerAsMakerProcessDepositTxMessage.class,
                        MakerRemovesOpenOffer.class,
                        SellerAsMakerFinalizesDepositTx.class,
                        SellerCreatesDelayedPayoutTx.class,
                        SellerSendDelayedPayoutTxSignatureRequest.class,

                        SellerProcessDelayedPayoutTxSignatureResponse.class,
                        SellerSignsDelayedPayoutTx.class,
                        SellerFinalizesDelayedPayoutTx.class,
                        SellerSendsDepositTxAndDelayedPayoutTxMessage.class,
                        SellerPublishesDepositTx.class,
                        SellerPublishesTradeStatistics.class,

                        SellerProcessCounterCurrencyTransferStartedMessage.class,
                        ApplyFilter.class,
                        MakerVerifyTakerFeePayment.class,

                        ApplyFilter.class,
                        MakerVerifyTakerFeePayment.class,
                        SellerSignAndFinalizePayoutTx.class,
                        SellerBroadcastPayoutTx.class,
                        SellerSendPayoutTxPublishedMessage.class
                        )
                ));
    }

    private void addGroup(String title, ObservableList<Class<? extends Task>> list) {
        final Tuple2<Label, ComboBox<Class<? extends Task>>> selectTaskToIntercept =
                addTopLabelComboBox(root, ++rowIndex, title, "Select task to intercept", 15);
        ComboBox<Class<? extends Task>> comboBox = selectTaskToIntercept.second;
        comboBox.setVisibleRowCount(list.size());
        comboBox.setItems(list);
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Class<? extends Task> item) {
                return item.getSimpleName();
            }

            @Override
            public Class<? extends Task> fromString(String s) {
                return null;
            }
        });
        comboBox.setOnAction(event -> Task.taskToIntercept = comboBox.getSelectionModel().getSelectedItem());
    }
}

