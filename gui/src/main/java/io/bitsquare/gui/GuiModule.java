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

import com.google.inject.Singleton;
import com.google.inject.name.Names;
import io.bitsquare.app.AppModule;
import io.bitsquare.app.AppOptionKeys;
import io.bitsquare.gui.common.fxml.FxmlViewLoader;
import io.bitsquare.gui.common.view.CachingViewLoader;
import io.bitsquare.gui.common.view.ViewFactory;
import io.bitsquare.gui.common.view.ViewLoader;
import io.bitsquare.gui.common.view.guice.InjectorViewFactory;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.offer.offerbook.OfferBook;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.BsqFormatter;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.gui.util.validation.*;
import io.bitsquare.locale.Res;
import javafx.stage.Stage;
import org.springframework.core.env.Environment;

import java.util.ResourceBundle;

public class GuiModule extends AppModule {

    private final Stage primaryStage;

    public GuiModule(Environment env, Stage primaryStage) {
        super(env);
        this.primaryStage = primaryStage;
    }

    @Override
    protected void configure() {
        bind(InjectorViewFactory.class).in(Singleton.class);
        bind(ViewFactory.class).to(InjectorViewFactory.class);

        bind(ResourceBundle.class).toInstance(Res.getResourceBundle());
        bind(ViewLoader.class).to(FxmlViewLoader.class).in(Singleton.class);
        bind(CachingViewLoader.class).in(Singleton.class);

        bind(Navigation.class).in(Singleton.class);

        bind(OfferBook.class).in(Singleton.class);
        bind(BSFormatter.class).in(Singleton.class);
        bind(BsqFormatter.class).in(Singleton.class);

        bind(IBANValidator.class).in(Singleton.class);
        bind(BtcValidator.class).in(Singleton.class);
        bind(FiatValidator.class).in(Singleton.class);
        bind(InputValidator.class).in(Singleton.class);
        bind(PasswordValidator.class).in(Singleton.class);
        bind(Transitions.class).in(Singleton.class);

        bind(Stage.class).toInstance(primaryStage);

        bindConstant().annotatedWith(Names.named(MainView.TITLE_KEY)).to(env.getRequiredProperty(AppOptionKeys.APP_NAME_KEY));
    }
}
