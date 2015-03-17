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

import io.bitsquare.BitsquareModule;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.gui.util.validation.BankAccountNumberValidator;
import io.bitsquare.gui.util.validation.BtcValidator;
import io.bitsquare.gui.util.validation.FiatValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.PasswordValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.offer.OfferBook;
import io.bitsquare.common.viewfx.view.CachingViewLoader;
import io.bitsquare.common.viewfx.view.ViewFactory;
import io.bitsquare.common.viewfx.view.ViewLoader;
import io.bitsquare.common.viewfx.view.fxml.FxmlViewLoader;
import io.bitsquare.common.viewfx.view.guice.InjectorViewFactory;

import com.google.inject.Singleton;
import com.google.inject.name.Names;

import java.util.ResourceBundle;

import javafx.stage.Stage;

import org.springframework.core.env.Environment;

public class GuiModule extends BitsquareModule {

    private final Stage primaryStage;

    public GuiModule(Environment env, Stage primaryStage) {
        super(env);
        this.primaryStage = primaryStage;
    }

    @Override
    protected void configure() {
        bind(InjectorViewFactory.class).in(Singleton.class);
        bind(ViewFactory.class).to(InjectorViewFactory.class);

        bind(ResourceBundle.class).toInstance(BSResources.getResourceBundle());
        bind(ViewLoader.class).to(FxmlViewLoader.class).in(Singleton.class);
        bind(CachingViewLoader.class).in(Singleton.class);

        bind(OfferBook.class).in(Singleton.class);
        bind(Navigation.class).in(Singleton.class);
        bind(OverlayManager.class).in(Singleton.class);
        bind(BSFormatter.class).in(Singleton.class);

        bind(BankAccountNumberValidator.class).in(Singleton.class);
        bind(BtcValidator.class).in(Singleton.class);
        bind(FiatValidator.class).in(Singleton.class);
        bind(InputValidator.class).in(Singleton.class);
        bind(PasswordValidator.class).in(Singleton.class);
        bind(Transitions.class).in(Singleton.class);

        bind(Stage.class).toInstance(primaryStage);
        Popups.primaryStage = primaryStage;

        bindConstant().annotatedWith(Names.named(MainView.TITLE_KEY)).to(env.getRequiredProperty(MainView.TITLE_KEY));
    }
}
