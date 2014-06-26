package io.bitsquare.gui.settings;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Utils;
import com.google.inject.Inject;
import io.bitsquare.BitSquare;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountTypeInfo;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.util.BitSquareValidator;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.locale.*;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.Reputation;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.io.IOException;
import java.net.URL;
import java.util.*;

// TODO separate in 2 view/controllers
public class SettingsController implements Initializable, ChildController, NavigationController
{
    private User user;
    private Settings settings;
    private Storage storage;
    private WalletFacade walletFacade;
    private MessageFacade messageFacade;
    private NavigationController navigationController;
    private ChildController childController;
    private ObservableList<Locale> languageList;
    private ObservableList<Country> countryList;
    private List<String> regionList;
    private ObservableList<Arbitrator> arbitratorList;
    private Region selectedRegion, selectedBankAccountRegion;

    @FXML
    private ListView<Locale> languagesListView;
    @FXML
    private ListView<Country> countriesListView;
    @FXML
    private ListView<Arbitrator> arbitratorsListView;
    @FXML
    private ComboBox<Locale> languageComboBox;
    @FXML
    private ComboBox<Region> regionComboBox, bankAccountRegionComboBox;
    @FXML
    private ComboBox<Country> countryComboBox, bankAccountCountryComboBox;
    @FXML
    private TextField bankAccountTitleTextField, bankAccountHolderNameTextField, bankAccountPrimaryIDTextField, bankAccountSecondaryIDTextField;
    @FXML
    private Button saveBankAccountButton, addBankAccountButton;
    @FXML
    private ComboBox<BankAccount> bankAccountComboBox;
    @FXML
    private ComboBox<BankAccountTypeInfo> bankAccountTypesComboBox;
    @FXML
    private ComboBox<Currency> bankAccountCurrencyComboBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SettingsController(User user, Settings settings, Storage storage, WalletFacade walletFacade, MessageFacade messageFacade)
    {
        this.user = user;
        this.settings = settings;
        this.storage = storage;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;

        Settings savedSettings = (Settings) storage.read(settings.getClass().getName());
        if (savedSettings != null)
        {
            settings.updateFromStorage(savedSettings);
            languageList = FXCollections.observableArrayList(settings.getAcceptedLanguageLocales());
            countryList = FXCollections.observableArrayList(settings.getAcceptedCountries());
            arbitratorList = FXCollections.observableArrayList(settings.getAcceptedArbitrators());
        }
        else
        {
            languageList = FXCollections.observableArrayList(new ArrayList<>());
            countryList = FXCollections.observableArrayList(new ArrayList<>());
            arbitratorList = FXCollections.observableArrayList(new ArrayList<>());

            addLanguage(LanguageUtil.getDefaultLanguageLocale());
            addCountry(CountryUtil.getDefaultCountry());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void updateArbitrators()
    {
        arbitratorList = FXCollections.observableArrayList(settings.getAcceptedArbitrators());
        initArbitrators();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        setupGeneralSettingsScreen();
        initBankAccountScreen();
        addMockArbitrator();
    }

    private void addMockArbitrator()
    {
        if (settings.getAcceptedArbitrators() == null || settings.getAcceptedArbitrators().size() == 0)
        {
            String pubKeyAsHex = Utils.bytesToHexString(new ECKey().getPubKey());
            String messagePubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(messageFacade.getPubKey());
            List<Locale> languages = new ArrayList<>();
            languages.add(LanguageUtil.getDefaultLanguageLocale());
            List<Arbitrator.METHOD> arbitrationMethods = new ArrayList<>();
            arbitrationMethods.add(Arbitrator.METHOD.TLS_NOTARY);
            List<Arbitrator.ID_VERIFICATION> idVerifications = new ArrayList<>();
            idVerifications.add(Arbitrator.ID_VERIFICATION.PASSPORT);
            idVerifications.add(Arbitrator.ID_VERIFICATION.GOV_ID);

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

            arbitratorList.add(arbitrator);
            settings.addAcceptedArbitrator(arbitrator);
            storage.write(settings.getClass().getName(), settings);

            try
            {
                messageFacade.addArbitrator(arbitrator);
            } catch (IOException e)
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void cleanup()
    {

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ChildController navigateToView(String fxmlView)
    {
        return navigateToView(fxmlView, "");
    }

    @Override
    public ChildController navigateToView(String fxmlView, String title)
    {
        if (childController != null)
            childController.cleanup();

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(fxmlView), Localisation.getResourceBundle());
        try
        {
            final Node view = loader.load();
            childController = loader.getController();
            childController.setNavigationController(this);

            final Stage rootStage = BitSquare.getStage();
            final Stage stage = new Stage();
            stage.setTitle(title);
            stage.setMinWidth(800);
            stage.setMinHeight(500);
            stage.setWidth(800);
            stage.setHeight(600);
            stage.setX(rootStage.getX() + 50);
            stage.setY(rootStage.getY() + 50);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(rootStage);
            Scene scene = new Scene((Parent) view, 800, 600);
            stage.setScene(scene);
            stage.setOnHidden(new EventHandler<WindowEvent>()
            {
                @Override
                public void handle(WindowEvent windowEvent)
                {
                    if (fxmlView.equals(NavigationController.ARBITRATOR_OVERVIEW))
                        updateArbitrators();
                }
            });
            stage.show();

            return childController;
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    // General Settings
    @FXML
    public void onAddLanguage(ActionEvent actionEvent)
    {
        addLanguage(languageComboBox.getSelectionModel().getSelectedItem());
        languageComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    public void onSelectRegion(ActionEvent actionEvent)
    {
        countryComboBox.setVisible(true);
        selectedRegion = regionComboBox.getSelectionModel().getSelectedItem();
        countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedRegion)));
    }

    @FXML
    public void onAddCountry(ActionEvent actionEvent)
    {
        addCountry(countryComboBox.getSelectionModel().getSelectedItem());
        countryComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    public void onAddArbitrator(ActionEvent actionEvent)
    {
        navigateToView(NavigationController.ARBITRATOR_OVERVIEW, "Arbitration selection");
    }


    // Bank Account Settings
    @FXML
    public void selectBankAccount(ActionEvent actionEvent)
    {
        BankAccount bankAccount = bankAccountComboBox.getSelectionModel().getSelectedItem();
        if (bankAccount != null && bankAccount != user.getCurrentBankAccount())
        {
            user.setCurrentBankAccount(bankAccount);
            storage.write(user.getClass().getName(), user);
            initBankAccountScreen();
        }
    }

    @FXML
    public void selectBankAccountType(ActionEvent actionEvent)
    {
        BankAccountTypeInfo bankAccountTypeInfo = bankAccountTypesComboBox.getSelectionModel().getSelectedItem();
        if (bankAccountTypeInfo != null)
        {
            bankAccountTitleTextField.setText("");
            bankAccountPrimaryIDTextField.setText("");
            bankAccountPrimaryIDTextField.setPromptText(bankAccountTypeInfo.getPrimaryIDName());
            bankAccountSecondaryIDTextField.setText("");
            bankAccountSecondaryIDTextField.setPromptText(bankAccountTypeInfo.getSecondaryIDName());
        }
    }

    @FXML
    public void onSelectBankAccountRegion(ActionEvent actionEvent)
    {
        bankAccountCountryComboBox.setVisible(true);
        selectedBankAccountRegion = bankAccountRegionComboBox.getSelectionModel().getSelectedItem();
        bankAccountCountryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedBankAccountRegion)));
    }

    @FXML
    public void onAddBankAccount(ActionEvent actionEvent)
    {
        resetBankAccountInput();
    }

    @FXML
    public void onRemoveBankAccount(ActionEvent actionEvent)
    {
        removeBankAccount();
    }

    @FXML
    public void onSaveBankAccount(ActionEvent actionEvent)
    {
        saveBankAccount();

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////
    // General Settings
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupGeneralSettingsScreen()
    {
        initLanguage();
        initCountry();
        initArbitrators();
    }

    private void initLanguage()
    {
        languagesListView.setCellFactory(new Callback<ListView<Locale>, ListCell<Locale>>()
        {
            @Override
            public ListCell<Locale> call(ListView<Locale> list)
            {
                return new ListCell<Locale>()
                {
                    final HBox hBox = new HBox();
                    final Label label = new Label();
                    final Button removeButton = new Button();
                    final ImageView icon = Icons.getIconImageView(Icons.REMOVE);

                    {
                        label.setPrefWidth(565);

                        icon.setMouseTransparent(true);

                        removeButton.setGraphic(icon);
                        removeButton.setId("icon-button");

                        hBox.setSpacing(3);
                        hBox.setAlignment(Pos.CENTER_LEFT);
                        hBox.getChildren().addAll(label, removeButton);
                    }

                    @Override
                    public void updateItem(final Locale item, boolean empty)
                    {
                        super.updateItem(item, empty);
                        if (item != null && !empty)
                        {
                            label.setText(item.getDisplayName());

                            removeButton.setOnAction(new EventHandler<ActionEvent>()
                            {
                                @Override
                                public void handle(ActionEvent actionEvent)
                                {
                                    removeLanguage(item);
                                }

                            });

                            setGraphic(hBox);
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        }
        );
        languagesListView.setItems(languageList);

        languageComboBox.setItems(FXCollections.observableArrayList(LanguageUtil.getAllLanguageLocales()));
        languageComboBox.setConverter(new StringConverter<Locale>()
        {
            @Override
            public String toString(Locale locale)
            {
                return locale.getDisplayLanguage();
            }

            @Override
            public Locale fromString(String s)
            {
                return null;
            }
        });
    }

    private void initCountry()
    {
        regionComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllRegions()));
        regionComboBox.setConverter(new StringConverter<Region>()
        {
            @Override
            public String toString(Region region)
            {
                return region.getName();
            }

            @Override
            public Region fromString(String s)
            {
                return null;
            }
        });

        countriesListView.setCellFactory(new Callback<ListView<Country>, ListCell<Country>>()
        {
            @Override
            public ListCell<Country> call(ListView<Country> list)
            {
                return new ListCell<Country>()
                {
                    final HBox hBox = new HBox();
                    final Label label = new Label();
                    final Button removeButton = new Button();
                    final ImageView icon = Icons.getIconImageView(Icons.REMOVE);

                    {
                        label.setPrefWidth(565);

                        icon.setMouseTransparent(true);

                        removeButton.setGraphic(icon);
                        removeButton.setId("icon-button");

                        hBox.setSpacing(3);
                        hBox.setAlignment(Pos.CENTER_LEFT);
                        hBox.getChildren().addAll(label, removeButton);
                    }


                    @Override
                    public void updateItem(final Country item, boolean empty)
                    {
                        super.updateItem(item, empty);
                        if (item != null && !empty)
                        {
                            label.setText(item.getName());

                            removeButton.setOnAction(new EventHandler<ActionEvent>()
                            {
                                @Override
                                public void handle(ActionEvent actionEvent)
                                {
                                    removeCountry(item);
                                }

                            });

                            setGraphic(hBox);
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        }
        );
        countriesListView.setItems(countryList);

        countryComboBox.setConverter(new StringConverter<Country>()
        {
            @Override
            public String toString(Country country)
            {
                return country.getName();
            }

            @Override
            public Country fromString(String s)
            {
                return null;
            }
        });
    }


    private void initArbitrators()
    {
        arbitratorsListView.setCellFactory(new Callback<ListView<Arbitrator>, ListCell<Arbitrator>>()
        {
            @Override
            public ListCell<Arbitrator> call(ListView<Arbitrator> list)
            {
                return new ListCell<Arbitrator>()
                {
                    final HBox hBox = new HBox();
                    final Label label = new Label();
                    final Button removeButton = new Button();
                    final ImageView icon = Icons.getIconImageView(Icons.REMOVE);

                    {
                        label.setPrefWidth(565);

                        icon.setMouseTransparent(true);

                        removeButton.setGraphic(icon);
                        removeButton.setId("icon-button");

                        hBox.setSpacing(3);
                        hBox.setAlignment(Pos.CENTER_LEFT);
                        hBox.getChildren().addAll(label, removeButton);
                    }


                    @Override
                    public void updateItem(final Arbitrator item, boolean empty)
                    {
                        super.updateItem(item, empty);
                        if (item != null && !empty)
                        {
                            label.setText(item.getName());

                            removeButton.setOnAction(new EventHandler<ActionEvent>()
                            {
                                @Override
                                public void handle(ActionEvent actionEvent)
                                {
                                    removeArbitrator(item);
                                }

                            });

                            setGraphic(hBox);
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        }
        );
        arbitratorsListView.setItems(arbitratorList);
    }

    private void addLanguage(Locale item)
    {
        if (!languageList.contains(item) && item != null)
        {
            languageList.add(item);
            settings.addAcceptedLanguageLocale(item);
            storage.write(settings.getClass().getName(), settings);
        }
    }

    private void removeLanguage(Locale item)
    {
        languageList.remove(item);
        settings.removeAcceptedLanguageLocale(item);
        storage.write(settings.getClass().getName(), settings);
    }

    private void addCountry(Country item)
    {
        if (!countryList.contains(item) && item != null)
        {
            countryList.add(item);
            settings.addAcceptedCountry(item);
            storage.write(settings.getClass().getName(), settings);
        }
    }

    private void removeCountry(Country item)
    {
        countryList.remove(item);
        settings.removeAcceptedCountry(item);
        storage.write(settings.getClass().getName(), settings);
    }

    private void removeArbitrator(Arbitrator item)
    {
        arbitratorList.remove(item);
        settings.removeAcceptedArbitrator(item);
        storage.write(settings.getClass().getName(), settings);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bank Account Settings
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initBankAccountScreen()
    {
        initBankAccountComboBox();
        initBankAccountTypesComboBox();
        initBankAccountCurrencyComboBox();
        initBankAccountCountryComboBox();

        BankAccount currentBankAccount = user.getCurrentBankAccount();
        if (currentBankAccount != null)
        {
            bankAccountTitleTextField.setText(currentBankAccount.getAccountTitle());
            bankAccountHolderNameTextField.setText(currentBankAccount.getAccountHolderName());
            bankAccountPrimaryIDTextField.setText(currentBankAccount.getAccountPrimaryID());
            bankAccountPrimaryIDTextField.setPromptText(currentBankAccount.getBankAccountTypeInfo().getPrimaryIDName());
            bankAccountSecondaryIDTextField.setText(currentBankAccount.getAccountSecondaryID());
            bankAccountSecondaryIDTextField.setPromptText(currentBankAccount.getBankAccountTypeInfo().getSecondaryIDName());
        }
        else
        {
            resetBankAccountInput();
        }

        //TODO
        bankAccountTypesComboBox.getSelectionModel().selectFirst();
        bankAccountCurrencyComboBox.getSelectionModel().selectFirst();
        bankAccountRegionComboBox.getSelectionModel().select(3);
        bankAccountCountryComboBox.getSelectionModel().select(5);
        bankAccountTitleTextField.setText("dummy");
        bankAccountHolderNameTextField.setText("dummy");
        bankAccountPrimaryIDTextField.setText("dummy");
        bankAccountSecondaryIDTextField.setText("dummy");
        if (user.getCurrentBankAccount() == null)
            onSaveBankAccount(null);
    }

    private void resetBankAccountInput()
    {
        bankAccountTitleTextField.setText("");
        bankAccountHolderNameTextField.setText("");
        bankAccountPrimaryIDTextField.setText("");
        bankAccountPrimaryIDTextField.setPromptText("");
        bankAccountSecondaryIDTextField.setText("");
        bankAccountSecondaryIDTextField.setPromptText("");

        bankAccountTypesComboBox.getSelectionModel().clearSelection();
        bankAccountCurrencyComboBox.getSelectionModel().clearSelection();
    }

    private void initBankAccountComboBox()
    {
        if (user.getBankAccounts().size() > 0)
        {
            bankAccountComboBox.setPromptText("Select bank account");
            bankAccountComboBox.setDisable(false);
            bankAccountComboBox.setItems(FXCollections.observableArrayList(user.getBankAccounts()));
            bankAccountComboBox.setConverter(new StringConverter<BankAccount>()
            {
                @Override
                public String toString(BankAccount bankAccount)
                {
                    return bankAccount.getAccountTitle();
                }

                @Override
                public BankAccount fromString(String s)
                {
                    return null;
                }
            });
            BankAccount currentBankAccount = user.getCurrentBankAccount();
            if (currentBankAccount != null)
            {
                int index = bankAccountComboBox.getItems().indexOf(currentBankAccount);
                bankAccountComboBox.getSelectionModel().select(index);
            }
        }
        else
        {
            bankAccountComboBox.setPromptText("No bank account available");
            bankAccountComboBox.setDisable(true);
        }
    }

    private void initBankAccountTypesComboBox()
    {
        bankAccountTypesComboBox.setItems(FXCollections.observableArrayList(BankAccountTypeInfo.getAllBankAccountTypeInfoObjects()));
        bankAccountTypesComboBox.setConverter(new StringConverter<BankAccountTypeInfo>()
        {
            @Override
            public String toString(BankAccountTypeInfo bankAccountTypeInfo)
            {
                return Localisation.get(bankAccountTypeInfo.getType().toString());
            }

            @Override
            public BankAccountTypeInfo fromString(String s)
            {
                return null;
            }
        });

        BankAccount currentBankAccount = user.getCurrentBankAccount();
        if (currentBankAccount != null)
        {
            int index = bankAccountTypesComboBox.getItems().indexOf(currentBankAccount.getBankAccountTypeInfo());
            bankAccountTypesComboBox.getSelectionModel().select(index);
        }
    }

    private void initBankAccountCurrencyComboBox()
    {
        bankAccountCurrencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllCurrencies()));
        bankAccountCurrencyComboBox.setConverter(new StringConverter<Currency>()
        {
            @Override
            public String toString(Currency currency)
            {
                return currency.getCurrencyCode() + " (" + currency.getDisplayName() + ")";
            }

            @Override
            public Currency fromString(String s)
            {
                return null;
            }
        });

        BankAccount currentBankAccount = user.getCurrentBankAccount();
        if (currentBankAccount != null)
        {
            Currency currentCurrency = currentBankAccount.getCurrency();
            int index = bankAccountCurrencyComboBox.getItems().indexOf(currentCurrency);
            bankAccountCurrencyComboBox.getSelectionModel().select(index);
        }
    }

    private void initBankAccountCountryComboBox()
    {
        bankAccountRegionComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllRegions()));
        bankAccountRegionComboBox.setConverter(new StringConverter<Region>()
        {
            @Override
            public String toString(Region region)
            {
                return region.getName();
            }

            @Override
            public Region fromString(String s)
            {
                return null;
            }
        });

        bankAccountCountryComboBox.setConverter(new StringConverter<Country>()
        {
            @Override
            public String toString(Country country)
            {
                return country.getName();
            }

            @Override
            public Country fromString(String s)
            {
                return null;
            }
        });

        BankAccount currentBankAccount = user.getCurrentBankAccount();
        if (currentBankAccount != null)
        {
            Country currentCountry = currentBankAccount.getCountry();
            Region currentRegion = currentCountry.getRegion();
            int regionIndex = bankAccountRegionComboBox.getItems().indexOf(currentRegion);
            bankAccountRegionComboBox.getSelectionModel().select(regionIndex);

            bankAccountCountryComboBox.setVisible(true);
            int countryIndex = bankAccountCountryComboBox.getItems().indexOf(currentCountry);
            bankAccountCountryComboBox.getSelectionModel().select(countryIndex);
        }
    }

    private void saveBankAccount()
    {
        if (verifyBankAccountData())
        {
            BankAccount bankAccount = new BankAccount(
                    bankAccountTypesComboBox.getSelectionModel().getSelectedItem(),
                    bankAccountCurrencyComboBox.getSelectionModel().getSelectedItem(),
                    bankAccountCountryComboBox.getSelectionModel().getSelectedItem(),
                    bankAccountTitleTextField.getText(),
                    bankAccountHolderNameTextField.getText(),
                    bankAccountPrimaryIDTextField.getText(),
                    bankAccountSecondaryIDTextField.getText());
            user.addBankAccount(bankAccount);

            storage.write(user.getClass().getName(), user);

            initBankAccountScreen();
        }
    }

    private void removeBankAccount()
    {
        user.removeCurrentBankAccount();

        storage.write(user.getClass().getName(), user);

        initBankAccountScreen();
    }

    private boolean verifyBankAccountData()
    {
        try
        {
            BitSquareValidator.textFieldsNotEmptyWithReset(bankAccountTitleTextField, bankAccountHolderNameTextField, bankAccountPrimaryIDTextField, bankAccountSecondaryIDTextField);

            BankAccountTypeInfo bankAccountTypeInfo = bankAccountTypesComboBox.getSelectionModel().getSelectedItem();
            BitSquareValidator.textFieldBankAccountPrimaryIDIsValid(bankAccountPrimaryIDTextField, bankAccountTypeInfo);
            BitSquareValidator.textFieldBankAccountSecondaryIDIsValid(bankAccountSecondaryIDTextField, bankAccountTypeInfo);

            return bankAccountTypesComboBox.getSelectionModel().getSelectedItem() != null
                    && bankAccountCountryComboBox.getSelectionModel().getSelectedItem() != null
                    && bankAccountCurrencyComboBox.getSelectionModel().getSelectedItem() != null;
        } catch (BitSquareValidator.ValidationException e)
        {
            return false;
        }
    }

}

