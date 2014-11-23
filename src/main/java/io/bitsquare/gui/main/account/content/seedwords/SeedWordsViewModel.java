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

package io.bitsquare.gui.main.account.content.seedwords;

import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.util.BSFormatter;

import com.google.inject.Inject;

import java.util.List;

import viewfx.ViewModel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

class SeedWordsViewModel implements ViewModel {

    final StringProperty seedWords = new SimpleStringProperty();

    @Inject
    public SeedWordsViewModel(WalletService walletService, BSFormatter formatter) {
        if (walletService.getWallet() != null) {
            List<String> mnemonicCode = walletService.getWallet().getKeyChainSeed().getMnemonicCode();
            if (mnemonicCode != null) {
                seedWords.set(formatter.mnemonicCodeToString(mnemonicCode));
            }
        }
    }
}
