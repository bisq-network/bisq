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
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.vo.BsqBlock;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;

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
public class BsqDashboardView extends ActivatableView<GridPane, Void> implements StateService.Listener {

    private final BsqBalanceUtil bsqBalanceUtil;
    private final StateService stateService;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;

    private int gridRow = 0;
    private TextField genesisIssueAmountTextField, compRequestIssueAmountTextField, availableAmountTextField, burntAmountTextField, allTxTextField,
            burntTxTextField/*, spentTxTextField*/,
            utxoTextField, priceTextField, marketCapTextField;
    private ChangeListener<Number> priceChangeListener;
    private HyperlinkWithIcon hyperlinkWithIcon;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqDashboardView(BsqBalanceUtil bsqBalanceUtil,
                             StateService stateService,
                             PriceFeedService priceFeedService,
                             Preferences preferences,
                             BsqFormatter bsqFormatter) {
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.stateService = stateService;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        addTitledGroupBg(root, ++gridRow, 11, Res.get("dao.wallet.dashboard.statistics"), Layout.GROUP_DISTANCE);

        addLabelTextField(root, gridRow, Res.get("dao.wallet.dashboard.genesisBlockHeight"),
                String.valueOf(stateService.getGenesisBlockHeight()), Layout.FIRST_ROW_AND_GROUP_DISTANCE);

        Label label = new AutoTooltipLabel(Res.get("dao.wallet.dashboard.genesisTxId"));
        GridPane.setRowIndex(label, ++gridRow);
        root.getChildren().add(label);
        hyperlinkWithIcon = new HyperlinkWithIcon(stateService.getGenesisTxId(), AwesomeIcon.EXTERNAL_LINK);
        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", stateService.getGenesisTxId())));
        GridPane.setRowIndex(hyperlinkWithIcon, gridRow);
        GridPane.setColumnIndex(hyperlinkWithIcon, 1);
        GridPane.setMargin(hyperlinkWithIcon, new Insets(0, 0, 0, -4));
        root.getChildren().add(hyperlinkWithIcon);

        genesisIssueAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.genesisIssueAmount")).second;
        compRequestIssueAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.compRequestIssueAmount")).second;
        availableAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.availableAmount")).second;
        burntAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.burntAmount")).second;

        allTxTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.allTx")).second;
        utxoTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.utxo")).second;
        // spentTxTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.spentTxo")).second;
        burntTxTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.burntTx")).second;

        priceTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.price")).second;
        marketCapTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.marketCap")).second;

        priceChangeListener = (observable, oldValue, newValue) -> updatePrice();
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();

        stateService.addListener(this);
        priceFeedService.updateCounterProperty().addListener(priceChangeListener);

        hyperlinkWithIcon.setOnAction(event -> GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + stateService.getGenesisTxId()));

        updateWithBsqBlockChainData();
        updatePrice();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        stateService.removeListener(this);
        priceFeedService.updateCounterProperty().removeListener(priceChangeListener);
        hyperlinkWithIcon.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        updateWithBsqBlockChainData();
    }


    private void updateWithBsqBlockChainData() {
        final Coin issuedAmountFromGenesis = stateService.getIssuedAmountAtGenesis();
        genesisIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromGenesis));

        final Coin issuedAmountFromCompRequests = stateService.getIssuedAmountFromCompRequests();
        compRequestIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromCompRequests));

        final Coin burntFee = stateService.getTotalBurntFee();
        final Coin availableAmount = issuedAmountFromGenesis.add(issuedAmountFromCompRequests).subtract(burntFee);

        availableAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(availableAmount));
        burntAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(burntFee));
        allTxTextField.setText(String.valueOf(stateService.getTransactions().size()));
        utxoTextField.setText(String.valueOf(stateService.getUnspentTxOutputs().size()));
        //spentTxTextField.setText(String.valueOf(stateService.getSpentTxOutputs().size()));
        burntTxTextField.setText(String.valueOf(stateService.getFeeTransactions().size()));
    }

    private void updatePrice() {
        final Coin issuedAmount = stateService.getIssuedAmountAtGenesis();
        final MarketPrice bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");
        if (bsqMarketPrice != null) {
            long bsqPrice = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(bsqMarketPrice.getPrice(), Altcoin.SMALLEST_UNIT_EXPONENT));
            priceTextField.setText(bsqFormatter.formatPrice(Price.valueOf("BSQ", bsqPrice)) + " BSQ/BTC");

            marketCapTextField.setText(bsqFormatter.formatMarketCap(bsqMarketPrice, priceFeedService.getMarketPrice("USD"), issuedAmount));
        }
    }
}

