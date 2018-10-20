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
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.util.Layout;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.blockchain.Block;
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

import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import static bisq.desktop.util.FormBuilder.addLabelHyperlinkWithIcon;
import static bisq.desktop.util.FormBuilder.addLabelTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BsqDashboardView extends ActivatableView<GridPane, Void> implements DaoStateListener {

    private final BsqBalanceUtil bsqBalanceUtil;
    private final DaoFacade daoFacade;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;

    private int gridRow = 0;
    private TextField genesisIssueAmountTextField, compRequestIssueAmountTextField, availableAmountTextField,
            burntAmountTextField, totalLockedUpAmountTextField, totalUnlockingAmountTextField,
            totalUnlockedAmountTextField, allTxTextField, burntTxTextField, utxoTextField, issuanceTxTextField,
            priceTextField, marketCapTextField;
    private ChangeListener<Number> priceChangeListener;
    private HyperlinkWithIcon hyperlinkWithIcon;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqDashboardView(BsqBalanceUtil bsqBalanceUtil,
                             DaoFacade daoFacade,
                             PriceFeedService priceFeedService,
                             Preferences preferences,
                             BsqFormatter bsqFormatter) {
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.daoFacade = daoFacade;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        addTitledGroupBg(root, ++gridRow, 4, Res.get("dao.wallet.dashboard.distribution"), Layout.GROUP_DISTANCE);

        genesisIssueAmountTextField = addLabelTextField(root, gridRow, Res.get("dao.wallet.dashboard.genesisIssueAmount"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        compRequestIssueAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.compRequestIssueAmount")).second;
        burntAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.burntAmount")).second;
        availableAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.availableAmount")).second;


        addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.wallet.dashboard.locked"), Layout.GROUP_DISTANCE);
        totalLockedUpAmountTextField = addLabelTextField(root, gridRow, Res.get("dao.wallet.dashboard.totalLockedUpAmount"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        totalUnlockingAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.totalUnlockingAmount")).second;
        totalUnlockedAmountTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.totalUnlockedAmount")).second;

        addTitledGroupBg(root, ++gridRow, 2, Res.get("dao.wallet.dashboard.market"), Layout.GROUP_DISTANCE);
        priceTextField = addLabelTextField(root, gridRow, Res.get("dao.wallet.dashboard.price"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        marketCapTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.marketCap")).second;


        addTitledGroupBg(root, ++gridRow, 6, Res.get("dao.wallet.dashboard.txDetails"), Layout.GROUP_DISTANCE);
        addLabelTextField(root, gridRow, Res.get("dao.wallet.dashboard.genesisBlockHeight"),
                String.valueOf(daoFacade.getGenesisBlockHeight()), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        hyperlinkWithIcon = addLabelHyperlinkWithIcon(root, ++gridRow, Res.get("dao.wallet.dashboard.genesisTxId"),
                daoFacade.getGenesisTxId(), preferences.getBsqBlockChainExplorer().txUrl + daoFacade.getGenesisTxId()).second;
        hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", daoFacade.getGenesisTxId())));
        allTxTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.allTx")).second;
        utxoTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.utxo")).second;
        issuanceTxTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.issuanceTx")).second;
        burntTxTextField = addLabelTextField(root, ++gridRow, Res.get("dao.wallet.dashboard.burntTx")).second;

        priceChangeListener = (observable, oldValue, newValue) -> updatePrice();
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();

        daoFacade.addBsqStateListener(this);
        priceFeedService.updateCounterProperty().addListener(priceChangeListener);

        updateWithBsqBlockChainData();
        updatePrice();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        daoFacade.removeBsqStateListener(this);
        priceFeedService.updateCounterProperty().removeListener(priceChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        updateWithBsqBlockChainData();
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateWithBsqBlockChainData() {
        Coin issuedAmountFromGenesis = daoFacade.getGenesisTotalSupply();
        genesisIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromGenesis));

        Coin issuedAmountFromCompRequests = Coin.valueOf(daoFacade.getTotalIssuedAmountFromCompRequests());
        compRequestIssueAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(issuedAmountFromCompRequests));

        Coin burntFee = Coin.valueOf(daoFacade.getTotalBurntFee());
        Coin totalLockedUpAmount = Coin.valueOf(daoFacade.getTotalLockupAmount());
        Coin totalUnlockingAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockingTxOutputs());
        Coin totalUnlockedAmount = Coin.valueOf(daoFacade.getTotalAmountOfUnLockedTxOutputs());
        Coin availableAmount = issuedAmountFromGenesis.add(issuedAmountFromCompRequests).subtract(burntFee);

        availableAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(availableAmount));
        burntAmountTextField.setText("-" + bsqFormatter.formatAmountWithGroupSeparatorAndCode(burntFee));
        totalLockedUpAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalLockedUpAmount));
        totalUnlockingAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockingAmount));
        totalUnlockedAmountTextField.setText(bsqFormatter.formatAmountWithGroupSeparatorAndCode(totalUnlockedAmount));
        allTxTextField.setText(String.valueOf(daoFacade.getTxs().size()));
        utxoTextField.setText(String.valueOf(daoFacade.getUnspentTxOutputs().size()));
        issuanceTxTextField.setText(String.valueOf(daoFacade.getIssuanceSet().size()));
        burntTxTextField.setText(String.valueOf(daoFacade.getFeeTxs().size()));
    }

    private void updatePrice() {
        Coin issuedAmount = daoFacade.getGenesisTotalSupply();
        MarketPrice bsqMarketPrice = priceFeedService.getMarketPrice("BSQ");
        if (bsqMarketPrice != null) {
            long bsqPrice = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(bsqMarketPrice.getPrice(), Altcoin.SMALLEST_UNIT_EXPONENT));
            priceTextField.setText(bsqFormatter.formatPrice(Price.valueOf("BSQ", bsqPrice)) + " BSQ/BTC");

            marketCapTextField.setText(bsqFormatter.formatMarketCap(bsqMarketPrice, priceFeedService.getMarketPrice("USD"), issuedAmount));
        } else {
            priceTextField.setText(Res.get("shared.na"));
            marketCapTextField.setText(Res.get("shared.na"));
        }
    }
}

