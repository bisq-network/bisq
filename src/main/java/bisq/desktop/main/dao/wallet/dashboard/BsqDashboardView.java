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

package bisq.desktop.main.dao.wallet.dashboard;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import javafx.beans.value.ChangeListener;

import static bisq.desktop.util.FormBuilder.addLabelTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BsqDashboardView extends ActivatableView<GridPane, Void> implements BsqBlockChain.Listener {

    private final BsqBalanceUtil bsqBalanceUtil;
    private final ReadableBsqBlockChain readableBsqBlockChain;
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
    private BsqDashboardView(BsqBalanceUtil bsqBalanceUtil,
                             ReadableBsqBlockChain readableBsqBlockChain,
                             PriceFeedService priceFeedService,
                             Preferences preferences,
                             BsqFormatter bsqFormatter) {
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        addTitledGroupBg(root, ++gridRow, 12, Res.get("dao.wallet.dashboard.statistics"), Layout.GROUP_DISTANCE);

        addLabelTextField(root, gridRow, Res.get("dao.wallet.dashboard.genesisBlockHeight"),
                String.valueOf(readableBsqBlockChain.getGenesisBlockHeight()), Layout.FIRST_ROW_AND_GROUP_DISTANCE);

        Label label = new AutoTooltipLabel(Res.get("dao.wallet.dashboard.genesisTxId"));
        GridPane.setRowIndex(label, ++gridRow);
        root.getChildren().add(label);
        hyperlinkWithIcon = new HyperlinkWithIcon(readableBsqBlockChain.getGenesisTxId(), AwesomeIcon.EXTERNAL_LINK);
        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", readableBsqBlockChain.getGenesisTxId())));
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

        readableBsqBlockChain.addListener(this);
        priceFeedService.updateCounterProperty().addListener(priceChangeListener);

        hyperlinkWithIcon.setOnAction(event -> GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + readableBsqBlockChain.getGenesisTxId()));

        updateWithBsqBlockChainData();
        updatePrice();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        readableBsqBlockChain.removeListener(this);
        priceFeedService.updateCounterProperty().removeListener(priceChangeListener);
        hyperlinkWithIcon.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBlockChain.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        updateWithBsqBlockChainData();
    }


    private void updateWithBsqBlockChainData() {
        issuedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(readableBsqBlockChain.getIssuedAmountAtGenesis()));
        final Coin burntFee = readableBsqBlockChain.getTotalBurntFee();
        final Coin availableAmount = readableBsqBlockChain.getIssuedAmountAtGenesis().subtract(burntFee);
        availableAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(availableAmount));
        burntAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(burntFee));
        allTxTextField.setText(String.valueOf(readableBsqBlockChain.getTransactions().size()));
        utxoTextField.setText(String.valueOf(readableBsqBlockChain.getUnspentTxOutputs().size()));
        spentTxTextField.setText(String.valueOf(readableBsqBlockChain.getSpentTxOutputs().size()));
        burntTxTextField.setText(String.valueOf(readableBsqBlockChain.getFeeTransactions().size()));
    }

    private void updatePrice() {
        final Coin issuedAmount = readableBsqBlockChain.getIssuedAmountAtGenesis();
        final MarketPrice bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");
        if (bsqMarketPrice != null) {
            long bsqPrice = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(bsqMarketPrice.getPrice(), Altcoin.SMALLEST_UNIT_EXPONENT));
            priceTextField.setText(bsqFormatter.formatPrice(Price.valueOf("BSQ", bsqPrice)) + " BSQ/BTC");

            marketCapTextField.setText(bsqFormatter.formatMarketCap(bsqMarketPrice, priceFeedService.getMarketPrice("USD"), issuedAmount));
        }
    }
}

