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

package io.bitsquare.gui.main.settings.application;

import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.user.BlockChainExplorer;
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
    private ComboBox<BlockChainExplorer> blockExplorerComboBox;
    private ComboBox<String> languageComboBox;
    private ComboBox<TradeCurrency> tradeCurrencyComboBox;

    private CheckBox useAnimationsCheckBox, useEffectsCheckBox, showPlaceOfferConfirmationCheckBox, showTakeOfferConfirmationCheckBox,
            autoSelectArbitratorsCheckBox;
    private int gridRow = 0;

    @Inject
    public PreferencesView(PreferencesViewModel model) {
        super(model);
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 3, "Preferences");
        tradeCurrencyComboBox = addLabelComboBox(root, gridRow, "Preferred currency:", Layout.FIRST_ROW_DISTANCE).second;
        languageComboBox = addLabelComboBox(root, ++gridRow, "Language:").second;
        // btcDenominationComboBox = addLabelComboBox(root, ++gridRow, "Bitcoin denomination:").second;
        blockExplorerComboBox = addLabelComboBox(root, ++gridRow, "Bitcoin block explorer:").second;

        addTitledGroupBg(root, ++gridRow, 5, "Display options", Layout.GROUP_DISTANCE);
        useAnimationsCheckBox = addLabelCheckBox(root, gridRow, "Use animations:", "", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        useEffectsCheckBox = addLabelCheckBox(root, ++gridRow, "Use effects:", "").second;
        showPlaceOfferConfirmationCheckBox = addLabelCheckBox(root, ++gridRow, "Show confirmation at place offer:", "").second;
        showTakeOfferConfirmationCheckBox = addLabelCheckBox(root, ++gridRow, "Show confirmation at take offer:", "").second;
        autoSelectArbitratorsCheckBox = addLabelCheckBox(root, ++gridRow, "Auto select arbitrators by language:", "").second;
    }

    @Override
    protected void activate() {
       /* btcDenominationComboBox.setDisable(true);
        btcDenominationComboBox.setItems(model.btcDenominations);
        btcDenominationComboBox.getSelectionModel().select(model.getBtcDenomination());
        btcDenominationComboBox.setOnAction(e -> model.onSelectBtcDenomination(btcDenominationComboBox.getSelectionModel().getSelectedItem()));*/

        tradeCurrencyComboBox.setItems(model.tradeCurrencies);
        tradeCurrencyComboBox.getSelectionModel().select(model.getTradeCurrency());
        tradeCurrencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getCodeAndName();
            }

            @Override
            public TradeCurrency fromString(String string) {
                return null;
            }
        });
        tradeCurrencyComboBox.setOnAction(e -> model.onSelectTradeCurrency(tradeCurrencyComboBox.getSelectionModel().getSelectedItem()));

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


        blockExplorerComboBox.setItems(model.blockExplorers);
        blockExplorerComboBox.getSelectionModel().select(model.getBlockExplorer());
        blockExplorerComboBox.setConverter(new StringConverter<BlockChainExplorer>() {
            @Override
            public String toString(BlockChainExplorer blockChainExplorer) {
                return blockChainExplorer.name;
            }

            @Override
            public BlockChainExplorer fromString(String string) {
                return null;
            }
        });
        blockExplorerComboBox.setOnAction(e -> model.onSelectBlockExplorer(blockExplorerComboBox.getSelectionModel().getSelectedItem()));


        useAnimationsCheckBox.setSelected(model.getUseAnimations());
        useAnimationsCheckBox.setOnAction(e -> model.onSelectUseAnimations(useAnimationsCheckBox.isSelected()));

        useEffectsCheckBox.setSelected(model.getUseEffects());
        useEffectsCheckBox.setOnAction(e -> model.onSelectUseEffects(useEffectsCheckBox.isSelected()));

        showPlaceOfferConfirmationCheckBox.setSelected(model.getShowPlaceOfferConfirmation());
        showPlaceOfferConfirmationCheckBox.setOnAction(e -> model.onSelectShowPlaceOfferConfirmation(showPlaceOfferConfirmationCheckBox.isSelected()));

        showTakeOfferConfirmationCheckBox.setSelected(model.getShowTakeOfferConfirmation());
        showTakeOfferConfirmationCheckBox.setOnAction(e -> model.onSelectShowTakeOfferConfirmation(showTakeOfferConfirmationCheckBox.isSelected()));

        autoSelectArbitratorsCheckBox.setSelected(model.getAutoSelectArbitrators());
        autoSelectArbitratorsCheckBox.setOnAction(e -> model.onSelectAutoSelectArbitratorsCheckBox(autoSelectArbitratorsCheckBox.isSelected()));
    }

    @Override
    protected void deactivate() {
        //btcDenominationComboBox.setOnAction(null);
        languageComboBox.setOnAction(null);
        tradeCurrencyComboBox.setOnAction(null);
        blockExplorerComboBox.setOnAction(null);
        useAnimationsCheckBox.setOnAction(null);
        useEffectsCheckBox.setOnAction(null);
        showPlaceOfferConfirmationCheckBox.setOnAction(null);
        showTakeOfferConfirmationCheckBox.setOnAction(null);
        autoSelectArbitratorsCheckBox.setOnAction(null);
    }
}
