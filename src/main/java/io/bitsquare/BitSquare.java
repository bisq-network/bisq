package io.bitsquare;

import com.google.bitcoin.core.Utils;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.BitSquareModule;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.User;
import io.bitsquare.util.MockData;
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

        // mock
        initSettings(settings, storage, user);

        settings.updateFromStorage((Settings) storage.read(settings.getClass().getName()));

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
            // settings.addAcceptedLanguageLocale(Locale.getDefault());
            settings.addAcceptedLanguageLocale(MockData.getLocales().get(0));
            settings.addAcceptedLanguageLocale(new Locale("en", "US"));
            settings.addAcceptedLanguageLocale(new Locale("es", "ES"));

            settings.getAcceptedCountryLocales().clear();
            //settings.addAcceptedCountryLocale(Locale.getDefault());
            settings.addAcceptedCountryLocale(MockData.getLocales().get(0));
            settings.addAcceptedCountryLocale(new Locale("en", "US"));
            settings.addAcceptedCountryLocale(new Locale("es", "ES"));

            settings.getAcceptedArbitrators().clear();
            settings.addAcceptedArbitrator(new Arbitrator("uid_1", "Charlie Boom", UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), "http://www.arbit.io/Charly_Boom", 0.1, 10, Utils.toNanoCoins("0.01")));
            settings.addAcceptedArbitrator(new Arbitrator("uid_2", "Tom Shang", UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), "http://www.arbit.io/Tom_Shang", 0, 1, Utils.toNanoCoins("0.001")));
            settings.addAcceptedArbitrator(new Arbitrator("uid_3", "Edward Snow", UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), "http://www.arbit.io/Edward_Swow", 0.2, 5, Utils.toNanoCoins("0.05")));
            settings.addAcceptedArbitrator(new Arbitrator("uid_4", "Julian Sander", UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), "http://www.arbit.io/Julian_Sander", 0, 20, Utils.toNanoCoins("0.1")));

            settings.setMinCollateral(0.01);
            settings.setMaxCollateral(0.1);

            storage.write(settings.getClass().getName(), settings);

            //initMockUser(storage, user);
        }
    }

    private void initMockUser(Storage storage, User user)
    {
        user.getBankAccounts().clear();

        BankAccount bankAccount1 = new BankAccount(new BankAccountType(BankAccountType.BankAccountTypeEnum.SEPA, "Iban", "Bic"),
                MockData.getCurrencies().get(0),
                MockData.getLocales().get(0),
                "Main EUR account",
                "Manfred Karrer",
                "564613242346",
                "23432432434"
        );
        user.addBankAccount(bankAccount1);

        BankAccount bankAccount2 = new BankAccount(new BankAccountType(BankAccountType.BankAccountTypeEnum.INTERNATIONAL, "Number", "ID"),
                MockData.getCurrencies().get(1),
                MockData.getLocales().get(2),
                "US account",
                "Manfred Karrer",
                "22312123123123123",
                "asdasdasdas"
        );
        user.addBankAccount(bankAccount2);

        user.setAccountID(UUID.randomUUID().toString());

        storage.write(user.getClass().getName(), user);
    }
}
