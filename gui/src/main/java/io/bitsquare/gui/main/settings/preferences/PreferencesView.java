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

package io.bitsquare.gui.main.settings.preferences;

import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.user.BlockChainExplorer;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class PreferencesView extends ActivatableViewAndModel<GridPane, PreferencesViewModel> {

    // not supported yet
    //private ComboBox<String> btcDenominationComboBox; 
    private ComboBox<BlockChainExplorer> blockChainExplorerComboBox;
    private ComboBox<String> languageComboBox;
    private ComboBox<TradeCurrency> preferredTradeCurrencyComboBox;

    private CheckBox useAnimationsCheckBox, useEffectsCheckBox, showNotificationsCheckBox, showInstructionsCheckBox,
            autoSelectArbitratorsCheckBox;
    private int gridRow = 0;
    //private InputTextField transactionFeeInputTextField;
    private ChangeListener<Boolean> transactionFeeFocusedListener;
    private Preferences preferences;

    @Inject
    public PreferencesView(PreferencesViewModel model, Preferences preferences) {
        super(model);
        this.preferences = preferences;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 4, "Preferences");
        preferredTradeCurrencyComboBox = addLabelComboBox(root, gridRow, "Preferred currency:", Layout.FIRST_ROW_DISTANCE).second;
        languageComboBox = addLabelComboBox(root, ++gridRow, "Language:").second;
        // btcDenominationComboBox = addLabelComboBox(root, ++gridRow, "Bitcoin denomination:").second;
        blockChainExplorerComboBox = addLabelComboBox(root, ++gridRow, "Bitcoin block explorer:").second;
        autoSelectArbitratorsCheckBox = addLabelCheckBox(root, ++gridRow, "Auto select arbitrators by language:", "").second;

        // TODO need a bit extra work to separate trade and non trade tx fees before it can be used
        /*transactionFeeInputTextField = addLabelInputTextField(root, ++gridRow, "Transaction fee (satoshi/byte):").second;
        transactionFeeFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutTransactionFeeTextField(oldValue, newValue);
        };*/

        addTitledGroupBg(root, ++gridRow, 4, "Display options", Layout.GROUP_DISTANCE);
        useAnimationsCheckBox = addLabelCheckBox(root, gridRow, "Use animations:", "", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        useEffectsCheckBox = addLabelCheckBox(root, ++gridRow, "Use effects:", "").second;
        showNotificationsCheckBox = addLabelCheckBox(root, ++gridRow, "Show notifications:", "").second;
        showInstructionsCheckBox = addLabelCheckBox(root, ++gridRow, "Show instruction popups:", "").second;
    }

    @Override
    protected void activate() {
       /* btcDenominationComboBox.setDisable(true);
        btcDenominationComboBox.setItems(model.btcDenominations);
        btcDenominationComboBox.getSelectionModel().select(model.getBtcDenomination());
        btcDenominationComboBox.setOnAction(e -> model.onSelectBtcDenomination(btcDenominationComboBox.getSelectionModel().getSelectedItem()));*/

        preferredTradeCurrencyComboBox.setItems(model.tradeCurrencies);
        preferredTradeCurrencyComboBox.getSelectionModel().select(preferences.getPreferredTradeCurrency());
        preferredTradeCurrencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String string) {
                return null;
            }
        });
        preferredTradeCurrencyComboBox.setOnAction(e -> preferences.setPreferredTradeCurrency(preferredTradeCurrencyComboBox.getSelectionModel().getSelectedItem()));

        languageComboBox.setItems(model.languageCodes);
        languageComboBox.getSelectionModel().select(model.getLanguageCode());
        languageComboBox.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String code) {
                return LanguageUtil.getDisplayName(code);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });
        languageComboBox.setOnAction(e -> model.onSelectLanguageCode(languageComboBox.getSelectionModel().getSelectedItem()));


        blockChainExplorerComboBox.setItems(model.blockExplorers);
        blockChainExplorerComboBox.getSelectionModel().select(preferences.getBlockChainExplorer());
        blockChainExplorerComboBox.setConverter(new StringConverter<BlockChainExplorer>() {
            @Override
            public String toString(BlockChainExplorer blockChainExplorer) {
                return blockChainExplorer.name;
            }

            @Override
            public BlockChainExplorer fromString(String string) {
                return null;
            }
        });
        blockChainExplorerComboBox.setOnAction(e -> preferences.setBlockChainExplorer(blockChainExplorerComboBox.getSelectionModel().getSelectedItem()));

        // transactionFeeInputTextField.textProperty().bindBidirectional(model.transactionFeePerByte);
        // transactionFeeInputTextField.focusedProperty().addListener(transactionFeeFocusedListener);

        useAnimationsCheckBox.setSelected(preferences.getUseAnimations());
        useAnimationsCheckBox.setOnAction(e -> preferences.setUseAnimations(useAnimationsCheckBox.isSelected()));

        useEffectsCheckBox.setSelected(preferences.getUseEffects());
        useEffectsCheckBox.setOnAction(e -> preferences.setUseEffects(useEffectsCheckBox.isSelected()));

        showNotificationsCheckBox.setSelected(preferences.getShowNotifications());
        showNotificationsCheckBox.setOnAction(e -> preferences.setShowNotifications(showNotificationsCheckBox.isSelected()));

        showInstructionsCheckBox.setSelected(preferences.getShowInstructions());
        showInstructionsCheckBox.setOnAction(e -> preferences.setShowInstructions(showInstructionsCheckBox.isSelected()));

        autoSelectArbitratorsCheckBox.setSelected(preferences.getAutoSelectArbitrators());
        autoSelectArbitratorsCheckBox.setOnAction(e -> preferences.setAutoSelectArbitrators(autoSelectArbitratorsCheckBox.isSelected()));
    }

    @Override
    protected void deactivate() {
        //btcDenominationComboBox.setOnAction(null);
        languageComboBox.setOnAction(null);
        preferredTradeCurrencyComboBox.setOnAction(null);
        blockChainExplorerComboBox.setOnAction(null);
        showNotificationsCheckBox.setOnAction(null);
        showInstructionsCheckBox.setOnAction(null);
        //  transactionFeeInputTextField.textProperty().unbind();
        ///  transactionFeeInputTextField.focusedProperty().removeListener(transactionFeeFocusedListener);
        useAnimationsCheckBox.setOnAction(null);
        useEffectsCheckBox.setOnAction(null);
        autoSelectArbitratorsCheckBox.setOnAction(null);
    }
}
