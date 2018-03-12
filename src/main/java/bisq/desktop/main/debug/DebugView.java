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
import bisq.desktop.util.FormBuilder;

import bisq.core.offer.availability.tasks.ProcessOfferAvailabilityResponse;
import bisq.core.offer.availability.tasks.SendOfferAvailabilityRequest;
import bisq.core.offer.placeoffer.tasks.AddToOfferBook;
import bisq.core.offer.placeoffer.tasks.CreateMakerFeeTx;
import bisq.core.offer.placeoffer.tasks.ValidateOffer;
import bisq.core.trade.protocol.tasks.CheckIfPeerIsBanned;
import bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import bisq.core.trade.protocol.tasks.buyer.BuyerSendCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.tasks.buyer.BuyerSetupPayoutTxListener;
import bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerCreatesAndSignsDepositTx;
import bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerSignPayoutTx;
import bisq.core.trade.protocol.tasks.buyer_as_taker.BuyerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.tasks.buyer_as_taker.BuyerAsTakerSignAndPublishDepositTx;
import bisq.core.trade.protocol.tasks.maker.MakerCreateAndSignContract;
import bisq.core.trade.protocol.tasks.maker.MakerProcessDepositTxPublishedMessage;
import bisq.core.trade.protocol.tasks.maker.MakerProcessPayDepositRequest;
import bisq.core.trade.protocol.tasks.maker.MakerSendPublishDepositTxRequest;
import bisq.core.trade.protocol.tasks.maker.MakerSetupDepositTxListener;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyArbitratorSelection;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyMediatorSelection;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerAccount;
import bisq.core.trade.protocol.tasks.maker.MakerVerifyTakerFeePayment;
import bisq.core.trade.protocol.tasks.seller.SellerBroadcastPayoutTx;
import bisq.core.trade.protocol.tasks.seller.SellerProcessCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.tasks.seller.SellerSendPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.seller.SellerSignAndFinalizePayoutTx;
import bisq.core.trade.protocol.tasks.seller_as_maker.SellerAsMakerCreatesAndSignsDepositTx;
import bisq.core.trade.protocol.tasks.seller_as_taker.SellerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.tasks.seller_as_taker.SellerAsTakerSignAndPublishDepositTx;
import bisq.core.trade.protocol.tasks.taker.CreateTakerFeeTx;
import bisq.core.trade.protocol.tasks.taker.TakerProcessPublishDepositTxRequest;
import bisq.core.trade.protocol.tasks.taker.TakerSelectArbitrator;
import bisq.core.trade.protocol.tasks.taker.TakerSelectMediator;
import bisq.core.trade.protocol.tasks.taker.TakerSendDepositTxPublishedMessage;
import bisq.core.trade.protocol.tasks.taker.TakerSendPayDepositRequest;
import bisq.core.trade.protocol.tasks.taker.TakerVerifyAndSignContract;
import bisq.core.trade.protocol.tasks.taker.TakerVerifyMakerAccount;
import bisq.core.trade.protocol.tasks.taker.TakerVerifyMakerFeePayment;

import bisq.common.taskrunner.Task;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.util.StringConverter;

import java.util.Arrays;

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

        addGroup("OfferAvailabilityProtocol: ",
                FXCollections.observableArrayList(Arrays.asList(
                        SendOfferAvailabilityRequest.class,
                        ProcessOfferAvailabilityResponse.class)
                ));

        addGroup("PlaceOfferProtocol: ",
                FXCollections.observableArrayList(Arrays.asList(
                        ValidateOffer.class,
                        CreateMakerFeeTx.class,
                        AddToOfferBook.class)
                ));

        addGroup("BuyerAsMakerProtocol: ",
                FXCollections.observableArrayList(Arrays.asList(
                        MakerProcessPayDepositRequest.class,
                        CheckIfPeerIsBanned.class,
                        MakerVerifyArbitratorSelection.class,
                        MakerVerifyMediatorSelection.class,
                        MakerVerifyTakerAccount.class,
                        MakerVerifyTakerFeePayment.class,
                        MakerCreateAndSignContract.class,
                        BuyerAsMakerCreatesAndSignsDepositTx.class,
                        MakerSetupDepositTxListener.class,
                        MakerSendPublishDepositTxRequest.class,

                        MakerProcessDepositTxPublishedMessage.class,
                        MakerVerifyTakerAccount.class,
                        MakerVerifyTakerFeePayment.class,
                        PublishTradeStatistics.class,

                        CheckIfPeerIsBanned.class,
                        MakerVerifyTakerAccount.class,
                        MakerVerifyTakerFeePayment.class,
                        BuyerAsMakerSignPayoutTx.class,
                        BuyerSendCounterCurrencyTransferStartedMessage.class,
                        BuyerSetupPayoutTxListener.class)
                ));
        addGroup("SellerAsTakerProtocol: ",
                FXCollections.observableArrayList(Arrays.asList(
                        TakerVerifyMakerAccount.class,
                        TakerVerifyMakerFeePayment.class,
                        TakerSelectArbitrator.class,
                        TakerSelectMediator.class,
                        CreateTakerFeeTx.class,
                        SellerAsTakerCreatesDepositTxInputs.class,
                        TakerSendPayDepositRequest.class,

                        TakerProcessPublishDepositTxRequest.class,
                        CheckIfPeerIsBanned.class,
                        TakerVerifyMakerAccount.class,
                        TakerVerifyMakerFeePayment.class,
                        TakerVerifyAndSignContract.class,
                        SellerAsTakerSignAndPublishDepositTx.class,
                        TakerSendDepositTxPublishedMessage.class,

                        SellerProcessCounterCurrencyTransferStartedMessage.class,
                        TakerVerifyMakerAccount.class,
                        TakerVerifyMakerFeePayment.class,

                        CheckIfPeerIsBanned.class,
                        TakerVerifyMakerAccount.class,
                        TakerVerifyMakerFeePayment.class,
                        SellerSignAndFinalizePayoutTx.class,
                        SellerBroadcastPayoutTx.class,
                        SellerSendPayoutTxPublishedMessage.class)
                ));
        addGroup("BuyerAsTakerProtocol: ",
                FXCollections.observableArrayList(Arrays.asList(
                        TakerSelectArbitrator.class,
                        TakerSelectMediator.class,
                        TakerVerifyMakerAccount.class,
                        TakerVerifyMakerFeePayment.class,
                        CreateTakerFeeTx.class,
                        BuyerAsTakerCreatesDepositTxInputs.class,
                        TakerSendPayDepositRequest.class,

                        TakerProcessPublishDepositTxRequest.class,
                        CheckIfPeerIsBanned.class,
                        TakerVerifyMakerAccount.class,
                        TakerVerifyMakerFeePayment.class,
                        TakerVerifyAndSignContract.class,
                        BuyerAsTakerSignAndPublishDepositTx.class,
                        TakerSendDepositTxPublishedMessage.class,

                        CheckIfPeerIsBanned.class,
                        TakerVerifyMakerAccount.class,
                        TakerVerifyMakerFeePayment.class,
                        BuyerAsMakerSignPayoutTx.class,
                        BuyerSendCounterCurrencyTransferStartedMessage.class,
                        BuyerSetupPayoutTxListener.class)
                ));
        addGroup("SellerAsMakerProtocol: ",
                FXCollections.observableArrayList(Arrays.asList(
                        MakerProcessPayDepositRequest.class,
                        CheckIfPeerIsBanned.class,
                        MakerVerifyArbitratorSelection.class,
                        MakerVerifyMediatorSelection.class,
                        MakerVerifyTakerAccount.class,
                        MakerVerifyTakerFeePayment.class,
                        MakerCreateAndSignContract.class,
                        SellerAsMakerCreatesAndSignsDepositTx.class,
                        MakerSetupDepositTxListener.class,
                        MakerSendPublishDepositTxRequest.class,

                        MakerProcessDepositTxPublishedMessage.class,
                        PublishTradeStatistics.class,
                        MakerVerifyTakerAccount.class,
                        MakerVerifyTakerFeePayment.class,

                        SellerProcessCounterCurrencyTransferStartedMessage.class,
                        MakerVerifyTakerAccount.class,
                        MakerVerifyTakerFeePayment.class,

                        CheckIfPeerIsBanned.class,
                        MakerVerifyTakerAccount.class,
                        MakerVerifyTakerFeePayment.class,
                        SellerSignAndFinalizePayoutTx.class,
                        SellerBroadcastPayoutTx.class,
                        SellerSendPayoutTxPublishedMessage.class)
                ));
    }

    private void addGroup(String title, ObservableList<Class> list) {
        ComboBox<Class> comboBox = FormBuilder.addLabelComboBox(root, ++rowIndex, title).second;
        comboBox.setVisibleRowCount(list.size());
        comboBox.setItems(list);
        comboBox.setPromptText("Select task to intercept");
        comboBox.setConverter(new StringConverter<Class>() {
            @Override
            public String toString(Class item) {
                return item.getSimpleName();
            }

            @Override
            public Class fromString(String s) {
                return null;
            }
        });
        comboBox.setOnAction(event -> Task.taskToIntercept = comboBox.getSelectionModel().getSelectedItem());
    }
}

