package io.bitsquare;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.BitSquareModule;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.Localisation;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.Reputation;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BitSquare extends Application
{
    private static final Logger log = LoggerFactory.getLogger(BitSquare.class);
    public static String ID = "bitsquare";
    private static Stage stage;
    private WalletFacade walletFacade;
    private MessageFacade messageFacade;

    public static void main(String[] args)
    {
        log.debug("Startup: main");
        if (args.length > 0)
            ID = args[0];

        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception
    {
        BitSquare.stage = stage;
        log.debug("Startup: start");
        final Injector injector = Guice.createInjector(new BitSquareModule());
        walletFacade = injector.getInstance(WalletFacade.class);

        messageFacade = injector.getInstance(MessageFacade.class);
        log.debug("Startup: messageFacade, walletFacade inited");

        // apply stored data
        final User user = injector.getInstance(User.class);
        final Settings settings = injector.getInstance(Settings.class);
        final Storage storage = injector.getInstance(Storage.class);
        user.updateFromStorage((User) storage.read(user.getClass().getName()));

        // mock
        //initSettings(settings, storage, user);


        settings.updateFromStorage((Settings) storage.read(settings.getClass().getName()));

        if (ID.length() > 0)
            stage.setTitle("BitSquare (" + ID + ")");
        else
            stage.setTitle("BitSquare");


        GuiceFXMLLoader.setInjector(injector);
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource("/io/bitsquare/gui/MainView.fxml"), Localisation.getResourceBundle());
        final Parent mainView = loader.load();

        final Scene scene = new Scene(mainView, 800, 600);
        stage.setScene(scene);

        final String global = getClass().getResource("/io/bitsquare/gui/global.css").toExternalForm();
        scene.getStylesheets().setAll(global);

        stage.setMinWidth(800);
        stage.setMinHeight(400);
        stage.setWidth(800);
        stage.setHeight(600);

        stage.show();
        log.debug("Startup: stage displayed");

        addMockArbitrator();
    }

    private void addMockArbitrator()
    {
        String pubKeyAsHex = walletFacade.getArbitratorPubKeyAsHex();
        String messagePubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(messageFacade.getPubKey());
        List<Locale> languages = new ArrayList<>();
        languages.add(LanguageUtil.getDefaultLanguageLocale());
        List<Arbitrator.METHODS> arbitrationMethods = new ArrayList<>();
        arbitrationMethods.add(Arbitrator.METHODS.TLS_NOTARY);
        List<Arbitrator.ID_VERIFICATIONS> idVerifications = new ArrayList<>();
        idVerifications.add(Arbitrator.ID_VERIFICATIONS.PASSPORT);
        idVerifications.add(Arbitrator.ID_VERIFICATIONS.GOV_ID);

        Arbitrator arbitrator = new Arbitrator(pubKeyAsHex,
                messagePubKeyAsHex,
                "Manfred Karrer",
                Arbitrator.ID_TYPE.REAL_LIFE_ID,
                languages,
                new Reputation(),
                1,
                0.01,
                0.001,
                10,
                0.1,
                arbitrationMethods,
                idVerifications,
                "http://bitsquare.io/",
                "Bla bla..."
        );

        try
        {
            messageFacade.addArbitrator(arbitrator);
        } catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @Override
    public void stop() throws Exception
    {
        walletFacade.shutDown();
        messageFacade.shutDown();

        super.stop();
    }

    public static Stage getStage()
    {
        return stage;
    }

   /* private void initSettings(Settings settings, Storage storage, User user)
    {
        Settings savedSettings = (Settings) storage.read(settings.getClass().getName());
        if (savedSettings == null)
        {
            // write default settings
            settings.getAcceptedCountries().clear();
            // settings.addAcceptedLanguageLocale(Locale.getDefault());
            settings.addAcceptedLanguageLocale(MockData.getLocales().get(0));
            settings.addAcceptedLanguageLocale(new Locale("en", "US"));
            settings.addAcceptedLanguageLocale(new Locale("es", "ES"));

            settings.getAcceptedCountries().clear();
            //settings.addAcceptedCountry(Locale.getDefault());
            settings.addAcceptedCountry(MockData.getLocales().get(0));
            settings.addAcceptedCountry(new Locale("en", "US"));
            settings.addAcceptedCountry(new Locale("de", "DE"));
            settings.addAcceptedCountry(new Locale("es", "ES"));


            settings.getAcceptedArbitrators().clear();
            settings.addAcceptedArbitrator(new Arbitrator("uid_1", "Charlie Boom", Utils.bytesToHexString(new ECKey().getPubKey()),
                    getMessagePubKey(), "http://www.arbit.io/Charly_Boom", 10, 50, Utils.toNanoCoins("0.01")));
            settings.addAcceptedArbitrator(new Arbitrator("uid_2", "Tom Shang", Utils.bytesToHexString(new ECKey().getPubKey()),
                    getMessagePubKey(), "http://www.arbit.io/Tom_Shang", 10, 100, Utils.toNanoCoins("0.001")));
            settings.addAcceptedArbitrator(new Arbitrator("uid_3", "Edward Snow", Utils.bytesToHexString(new ECKey().getPubKey()),
                    getMessagePubKey(), "http://www.arbit.io/Edward_Swow", 20, 50, Utils.toNanoCoins("0.05")));
            settings.addAcceptedArbitrator(new Arbitrator("uid_4", "Julian Sander", Utils.bytesToHexString(new ECKey().getPubKey()),
                    getMessagePubKey(), "http://www.arbit.io/Julian_Sander", 10, 20, Utils.toNanoCoins("0.1")));

            settings.setMinCollateral(1);
            settings.setMaxCollateral(10);

            storage.write(settings.getClass().getName(), settings);

            //initMockUser(storage, user);
        }
    }     */

  /*  private String getMessagePubKey()
    {
        try
        {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
            keyGen.initialize(1024);
            KeyPair generatedKeyPair = keyGen.genKeyPair();
            PublicKey pubKey = generatedKeyPair.getPublic();
            return DSAKeyUtil.getHexStringFromPublicKey(pubKey);
        } catch (Exception e2)
        {
            return null;
        }
    }     */


  /*  private void initMockUser(Storage storage, User user)
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

        user.setAccountID(Utils.bytesToHexString(new ECKey().getPubKey()));

        storage.write(user.getClass().getName(), user);
    } */
}
