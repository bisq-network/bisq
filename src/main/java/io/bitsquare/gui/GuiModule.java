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

package io.bitsquare.gui;

import io.bitsquare.di.AbstractBitsquareModule;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.trade.offerbook.OfferBook;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.validation.BankAccountNumberValidator;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.PasswordValidator;

import java.util.Properties;

import javafx.stage.Stage;

public class GuiModule extends AbstractBitsquareModule {

    private final Stage primaryStage;

    public GuiModule(Properties properties, Stage primaryStage) {
        super(properties);
        this.primaryStage = primaryStage;
    }

    @Override
    protected void configure() {
        bind(OfferBook.class).asEagerSingleton();
        bind(Navigation.class).asEagerSingleton();
        bind(OverlayManager.class).asEagerSingleton();
        bind(BSFormatter.class).asEagerSingleton();

        bind(BankAccountNumberValidator.class).asEagerSingleton();
        bind(BtcValidator.class).asEagerSingleton();
        bind(FiatValidator.class).asEagerSingleton();
        bind(InputValidator.class).asEagerSingleton();
        bind(PasswordValidator.class).asEagerSingleton();

        bind(Stage.class).toInstance(primaryStage);
        Popups.primaryStage = primaryStage;
        Help.primaryStage = primaryStage;
    }
}
