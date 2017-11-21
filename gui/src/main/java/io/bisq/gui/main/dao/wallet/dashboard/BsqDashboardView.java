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

package io.bisq.gui.main.dao.wallet.dashboard;

import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.locale.Res;
import io.bisq.common.monetary.Altcoin;
import io.bisq.common.monetary.Price;
import io.bisq.common.util.MathUtils;
import io.bisq.core.dao.blockchain.BsqBlockchainManager;
import io.bisq.core.dao.blockchain.BsqChainStateListener;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.provider.price.MarketPrice;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.user.Preferences;
import io.bisq.gui.common.view.ActivatableView;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.main.dao.wallet.BsqBalanceUtil;
import io.bisq.gui.util.BsqFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.Layout;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import static io.bisq.gui.util.FormBuilder.addLabelTextField;
import static io.bisq.gui.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BsqDashboardView extends ActivatableView<GridPane, Void> implements BsqChainStateListener {

    private final BsqBalanceUtil bsqBalanceUtil;
    private final BsqBlockchainManager bsqBlockchainManager;
    private final BsqChainState bsqChainState;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;

    private int gridRow = 0;
    private TextField issuedAmountTextField, availableAmountTextField, burntAmountTextField, allTxTextField,
            burntTxTextField, spentTxTextField,
            utxoTextField, priceTextField, marketCapTextField;
    private ChangeListener<Number> priceChangeListener;
    private HyperlinkWithIcon hyperlinkWithIcon;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqDashboardView(BsqBalanceUtil bsqBalanceUtil, BsqBlockchainManager bsqBlockchainManager,
                             BsqChainState bsqChainState, PriceFeedService priceFeedService,
                             Preferences preferences, BsqFormatter bsqFormatter) {
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.bsqBlockchainManager = bsqBlockchainManager;
        this.bsqChainState = bsqChainState;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        addTitledGroupBg(root, ++gridRow, 12, Res.get("dao.wallet.dashboard.statistics"), Layout.GROUP_DISTANCE);

        addLabelTextField(root, gridRow, Res.get("dao.wallet.dashboard.genesisBlockHeight"),
                String.valueOf(bsqChainState.getGenesisBlockHeight()), Layout.FIRST_ROW_AND_GROUP_DISTANCE);

        Label label = new Label(Res.get("dao.wallet.dashboard.genesisTxId"));
        GridPane.setRowIndex(label, ++gridRow);
        root.getChildren().add(label);
        hyperlinkWithIcon = new HyperlinkWithIcon(bsqChainState.getGenesisTxId(), AwesomeIcon.EXTERNAL_LINK);
        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", bsqChainState.getGenesisTxId())));
        GridPane.setRowIndex(hyperlinkWithIcon, gridRow);
        GridPane.setColumnIndex(hyperlinkWithIcon, 1);
        GridPane.setMargin(hyperlinkWithIcon, new Insets(0, 0, 0, -4));
        root.getChildren().add(hyperlinkWithIcon);

        issuedAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.issuedAmount")).second;
        availableAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.availableAmount")).second;
        burntAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.burntAmount")).second;

        allTxTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.allTx")).second;
        utxoTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.utxo")).second;
        spentTxTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.spentTxo")).second;
        burntTxTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.burntTx")).second;

        priceTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.price")).second;
        marketCapTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.marketCap")).second;

        priceChangeListener = (observable, oldValue, newValue) -> updatePrice();
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();

        bsqBlockchainManager.addBsqChainStateListener(this);
        priceFeedService.updateCounterProperty().addListener(priceChangeListener);

        hyperlinkWithIcon.setOnAction(event -> GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + bsqChainState.getGenesisTxId()));

        onBsqChainStateChanged();
        updatePrice();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        bsqBlockchainManager.removeBsqChainStateListener(this);
        priceFeedService.updateCounterProperty().removeListener(priceChangeListener);
        hyperlinkWithIcon.setOnAction(null);
    }


    @Override
    public void onBsqChainStateChanged() {
        issuedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(bsqChainState.getIssuedAmount()));
        final Coin burntFee = bsqChainState.getTotalBurntFee();
        final Coin availableAmount = bsqChainState.getIssuedAmount().subtract(burntFee);
        availableAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(availableAmount));
        burntAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(burntFee));
        allTxTextField.setText(String.valueOf(bsqChainState.getTransactions().size()));
        utxoTextField.setText(String.valueOf(bsqChainState.getUnspentTxOutputs().size()));
        spentTxTextField.setText(String.valueOf(bsqChainState.getSpentTxOutputs().size()));
        burntTxTextField.setText(String.valueOf(bsqChainState.getFeeTransactions().size()));
    }

    private void updatePrice() {
        final Coin issuedAmount = bsqChainState.getIssuedAmount();
        final MarketPrice bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");
        if (bsqMarketPrice != null) {
            long bsqPrice = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(bsqMarketPrice.getPrice(), Altcoin.SMALLEST_UNIT_EXPONENT));
            priceTextField.setText(bsqFormatter.formatPrice(Price.valueOf("BSQ", bsqPrice)) + " BSQ/BTC");

            marketCapTextField.setText(bsqFormatter.formatMarketCap(bsqMarketPrice, priceFeedService.getMarketPrice("USD"), issuedAmount));
        }
    }
}

