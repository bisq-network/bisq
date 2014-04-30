package io.bitsquare;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.BitSquareModule;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.User;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.UUID;

public class BitSquare extends Application
{
    private static final Logger log = LoggerFactory.getLogger(BitSquare.class);
    private WalletFacade walletFacade;

    public static void main(String[] args)
    {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception
    {
        final Injector injector = Guice.createInjector(new BitSquareModule());
        walletFacade = injector.getInstance(WalletFacade.class);

        // apply stored data
        final User user = injector.getInstance(User.class);
        final Settings settings = injector.getInstance(Settings.class);
        final Storage storage = injector.getInstance(Storage.class);
        user.updateFromStorage((User) storage.read(user.getClass().getName()));

        settings.updateFromStorage((Settings) storage.read(settings.getClass().getName()));
        initSettings(settings, storage, user);

        stage.setTitle("BitSquare");

        GuiceFXMLLoader.setInjector(injector);
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource("/io/bitsquare/gui/MainView.fxml"), Localisation.getResourceBundle());
        final Parent mainView = loader.load();

        final Scene scene = new Scene(mainView, 800, 600);
        stage.setScene(scene);

        final String global = getClass().getResource("/io/bitsquare/gui/global.css").toExternalForm();
        scene.getStylesheets().setAll(global);

        stage.setMinWidth(740);
        stage.setMinHeight(400);
        stage.setWidth(800);
        stage.setHeight(600);

        stage.show();
    }

    @Override
    public void stop() throws Exception
    {
        walletFacade.shutDown();

        super.stop();
    }

    private void initSettings(Settings settings, Storage storage, User user)
    {
        Settings savedSettings = (Settings) storage.read(settings.getClass().getName());
        if (savedSettings == null)
        {
            // write default settings
            settings.getAcceptedCountryLocales().clear();
            settings.getAcceptedLanguageLocales().clear();

            settings.addAcceptedLanguageLocale(Locale.getDefault());
            settings.addAcceptedCountryLocale(Locale.getDefault());

            //TODO mock
            settings.addAcceptedLanguageLocale(new Locale("en", "US"));
            settings.addAcceptedLanguageLocale(new Locale("es", "ES"));

            settings.addAcceptedCountryLocale(new Locale("de", "AT"));
            settings.addAcceptedCountryLocale(new Locale("en", "US"));
            settings.addAcceptedCountryLocale(new Locale("es", "ES"));

            settings.addArbitrator(new Arbitrator("Charly Boom", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "http://www.arbit.io/Charly_Boom"));
            settings.addArbitrator(new Arbitrator("Tom Shang", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "http://www.arbit.io/Tom_Shang"));
            settings.addArbitrator(new Arbitrator("Edward Snow", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "http://www.arbit.io/Edward_Swow"));
            settings.addArbitrator(new Arbitrator("Julian Sander", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "http://www.arbit.io/Julian_Sander"));

            storage.write(settings.getClass().getName(), settings);

            /*
            BankAccount bankAccount1 = new BankAccount(new BankAccountType(BankAccountType.BankAccountTypeEnum.SEPA,"Iban", "Bic"),
                    MockData.getCurrencies().get(0),
                    MockData.getLocales().get(0),
                    "Main account",
                    "Manfred Karrer",
                    "564613242346",
                    "23432432434"
                    );
            BankAccount bankAccount2 = new BankAccount(new BankAccountType(BankAccountType.BankAccountTypeEnum.OK_PAY,"Number", "ID"),
                    MockData.getCurrencies().get(0),
                    MockData.getLocales().get(0),
                    "OK account",
                    "Manfred Karrer",
                    "22312123123123123",
                    "asdasdasdas"
            );
            user.addBankAccount(bankAccount2);
            user.addBankAccount(bankAccount1);
            user.setAccountID(UUID.randomUUID().toString());
            storage.write(user.getClass().getName(), user);
            */
        }
        else
        {
            settings.updateFromStorage(savedSettings);
        }
    }
}
