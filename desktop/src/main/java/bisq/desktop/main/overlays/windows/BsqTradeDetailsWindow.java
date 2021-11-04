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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;

import static bisq.desktop.util.FormBuilder.*;

public class BsqTradeDetailsWindow extends Overlay<BsqTradeDetailsWindow> {
    private final CoinFormatter formatter;
    private BsqFormatter bsqFormatter;
    private final TradeManager tradeManager;
    private final BsqWalletService bsqWalletService;
    private BsqSwapTrade bsqSwapTrade;
    private ChangeListener<Number> changeListener;
    private TextArea textArea;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqTradeDetailsWindow(@Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                                 BsqFormatter bsqFormatter,
                                 TradeManager tradeManager,
                                 BsqWalletService bsqWalletService) {
        this.formatter = formatter;
        this.bsqFormatter = bsqFormatter;
        this.tradeManager = tradeManager;
        this.bsqWalletService = bsqWalletService;
        type = Type.Confirmation;
    }

    public void show(BsqSwapTrade bsqSwapTrade) {
        this.bsqSwapTrade = bsqSwapTrade;

        rowIndex = -1;
        width = 918;
        createGridPane();
        addContent();
        display();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void cleanup() {
        if (textArea != null)
            textArea.scrollTopProperty().addListener(changeListener);
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
    }

    private void addContent() {
        Offer offer = bsqSwapTrade.getOffer();

        int rows = 5;
        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("tradeDetailsWindow.bsqSwap.headline"));

        boolean myOffer = tradeManager.isMyOffer(offer);
        String bsqDirectionInfo;
        String btcDirectionInfo;
        String toReceive = " " + Res.get("shared.toReceive");
        String toSpend = " " + Res.get("shared.toSpend");
        String offerType = Res.get("shared.offerType");
        String minus = " (- ";
        String plus = " (+ ";
        String minerFeePostFix = Res.get("tradeDetailsWindow.txFee") + ")";
        String tradeFeePostFix = Res.get("shared.tradeFee") + ")";
        String btcAmount = formatter.formatCoinWithCode(bsqSwapTrade.getAmountAsLong());
        String bsqAmount = VolumeUtil.formatVolumeWithCode(bsqSwapTrade.getVolume());
        if (tradeManager.isBuyer(offer)) {
            addConfirmationLabelLabel(gridPane, rowIndex, offerType,
                    DisplayUtils.getDirectionForBuyer(myOffer, offer.getCurrencyCode()), Layout.TWICE_FIRST_ROW_DISTANCE);
            bsqDirectionInfo = toSpend;
            btcDirectionInfo = toReceive;
            btcAmount += minus + minerFeePostFix;
            bsqAmount += plus + tradeFeePostFix;
        } else {
            addConfirmationLabelLabel(gridPane, rowIndex, offerType,
                    DisplayUtils.getDirectionForSeller(myOffer, offer.getCurrencyCode()), Layout.TWICE_FIRST_ROW_DISTANCE);
            bsqDirectionInfo = toReceive;
            btcDirectionInfo = toSpend;
            btcAmount += plus + minerFeePostFix;
            bsqAmount += minus + tradeFeePostFix;
        }

        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.btcAmount") + btcDirectionInfo, btcAmount);


        addConfirmationLabelLabel(gridPane, ++rowIndex,
                VolumeUtil.formatVolumeLabel(offer.getCurrencyCode()) + bsqDirectionInfo,
                bsqAmount);

        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.tradePrice"),
                FormattingUtils.formatPrice(bsqSwapTrade.getPrice()));
        String paymentMethodText = Res.get(offer.getPaymentMethod().getId());
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.paymentMethod"), paymentMethodText);

        // details
        rows = 3;
        Transaction transaction = bsqSwapTrade.getTransaction(bsqWalletService);
        if (transaction != null)
            rows++;
        if (bsqSwapTrade.hasFailed())
            rows += 2;
        if (bsqSwapTrade.getTradingPeerNodeAddress() != null)
            rows++;

        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("shared.details"), Layout.GROUP_DISTANCE);
        addConfirmationLabelTextFieldWithCopyIcon(gridPane, rowIndex, Res.get("shared.tradeId"),
                bsqSwapTrade.getId(), Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE);
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.tradeDate"),
                DisplayUtils.formatDateTime(bsqSwapTrade.getDate()));

        // tx fee, would be good to store it in process model to not need to re-calculate it here
       /* String txFee = Res.get("shared.makerTxFee", formatter.formatCoinWithCode(offer.getTxFee())) +
                " / " + Res.get("shared.takerTxFee", formatter.formatCoinWithCode(bsqSwapTrade.gettx())); //todo
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.txFee"), txFee);*/

        String tradeFee = Res.get("shared.makerTxFee", bsqFormatter.formatCoinWithCode(bsqSwapTrade.getMakerFeeAsLong())) +
                " / " + Res.get("shared.takerTxFee", bsqFormatter.formatCoinWithCode(bsqSwapTrade.getTakerFeeAsLong()));
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("shared.tradeFee"), tradeFee);


        if (bsqSwapTrade.getTradingPeerNodeAddress() != null)
            addConfirmationLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.tradingPeersOnion"),
                    bsqSwapTrade.getTradingPeerNodeAddress().getFullAddress());


        if (transaction != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.bsqSwap.txId"),
                    transaction.getTxId().toString());

        if (bsqSwapTrade.hasFailed()) {
            textArea = addConfirmationLabelTextArea(gridPane, ++rowIndex,
                    Res.get("shared.errorMessage"), "", 0).second;
            textArea.setText(bsqSwapTrade.getErrorMessage());
            textArea.setEditable(false);
            //TODO paint red

            IntegerProperty count = new SimpleIntegerProperty(20);
            int rowHeight = 10;
            textArea.prefHeightProperty().bindBidirectional(count);
            changeListener = (ov, old, newVal) -> {
                if (newVal.intValue() > rowHeight)
                    count.setValue(count.get() + newVal.intValue() + 10);
            };
            textArea.scrollTopProperty().addListener(changeListener);
            textArea.setScrollTop(30);

            addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("tradeDetailsWindow.tradeState"),
                    bsqSwapTrade.getState().name());
        }

        Button closeButton = addButtonAfterGroup(gridPane, ++rowIndex, Res.get("shared.close"));
        GridPane.setColumnIndex(closeButton, 1);
        GridPane.setHalignment(closeButton, HPos.RIGHT);
        closeButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }
}
