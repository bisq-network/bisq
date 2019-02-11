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

package bisq.desktop;

import bisq.desktop.common.fxml.FxmlViewLoader;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.ViewFactory;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.common.view.guice.InjectorViewFactory;
import bisq.desktop.main.dao.bonding.BondingViewUtils;
import bisq.desktop.main.funds.transactions.DisplayedTransactionsFactory;
import bisq.desktop.main.funds.transactions.TradableRepository;
import bisq.desktop.main.funds.transactions.TransactionAwareTradableFactory;
import bisq.desktop.main.funds.transactions.TransactionListItemFactory;
import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.overlays.notifications.NotificationCenter;
import bisq.desktop.main.overlays.windows.TorNetworkSettingsWindow;
import bisq.desktop.main.presentation.DaoPresentation;
import bisq.desktop.main.presentation.MarketPricePresentation;
import bisq.desktop.util.Transitions;

import bisq.core.app.AppOptionKeys;
import bisq.core.locale.Res;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.common.app.AppModule;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;
import com.google.inject.name.Names;

import java.util.ResourceBundle;

public class DesktopModule extends AppModule {


    public DesktopModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(InjectorViewFactory.class).in(Singleton.class);
        bind(ViewFactory.class).to(InjectorViewFactory.class);
        bind(CachingViewLoader.class).in(Singleton.class);

        bind(ResourceBundle.class).toInstance(Res.getResourceBundle());
        bind(ViewLoader.class).to(FxmlViewLoader.class).in(Singleton.class);
        bind(CachingViewLoader.class).in(Singleton.class);

        bind(Navigation.class).in(Singleton.class);
        bind(NotificationCenter.class).in(Singleton.class);

        bind(OfferBook.class).in(Singleton.class);
        bind(BSFormatter.class).in(Singleton.class);
        bind(BsqFormatter.class).in(Singleton.class);
        bind(TorNetworkSettingsWindow.class).in(Singleton.class);
        bind(MarketPricePresentation.class).in(Singleton.class);
        bind(DaoPresentation.class).in(Singleton.class);

        bind(Transitions.class).in(Singleton.class);

        bind(TradableRepository.class).in(Singleton.class);
        bind(TransactionListItemFactory.class).in(Singleton.class);
        bind(TransactionAwareTradableFactory.class).in(Singleton.class);
        bind(DisplayedTransactionsFactory.class).in(Singleton.class);

        bind(BondingViewUtils.class).in(Singleton.class);

        bindConstant().annotatedWith(Names.named(AppOptionKeys.APP_NAME_KEY)).to(environment.getRequiredProperty(AppOptionKeys.APP_NAME_KEY));
    }
}
