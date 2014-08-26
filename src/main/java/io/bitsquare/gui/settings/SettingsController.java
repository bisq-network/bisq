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

package io.bitsquare.gui.settings;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Utils;
import io.bitsquare.BitSquare;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.CachedViewController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.ViewController;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.util.BitSquareValidator;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.locale.*;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Persistence;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.Reputation;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javax.inject.Inject;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

// TODO separate in 2 view/controllers
public class SettingsController extends CachedViewController
{
    private final User user;
    private final Settings settings;
    private final Persistence persistence;
    private final MessageFacade messageFacade;

    private final ObservableList<Locale> languageList;
    private final ObservableList<Country> countryList;
    private ViewController childController;
    private ObservableList<Arbitrator> arbitratorList;

    @FXML private ListView<Locale> languagesListView;
    @FXML private ListView<Country> countriesListView;
    @FXML private ListView<Arbitrator> arbitratorsListView;
    @FXML private ComboBox<Locale> languageComboBox;
    @FXML private ComboBox<Region> regionComboBox, bankAccountRegionComboBox;
    @FXML private ComboBox<Country> countryComboBox, bankAccountCountryComboBox;
    @FXML private TextField bankAccountTitleTextField, bankAccountHolderNameTextField, bankAccountPrimaryIDTextField, bankAccountSecondaryIDTextField;
    @FXML private Button saveBankAccountButton, addBankAccountButton;
    @FXML private ComboBox<BankAccount> bankAccountComboBox;
    @FXML private ComboBox<BankAccountType> bankAccountTypesComboBox;
    @FXML private ComboBox<Currency> bankAccountCurrencyComboBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SettingsController(User user, Settings settings, Persistence persistence, MessageFacade messageFacade)
    {
        this.user = user;
        this.settings = settings;
        this.persistence = persistence;
        this.messageFacade = messageFacade;

        Settings persistedSettings = (Settings) persistence.read(settings);
        if (persistedSettings != null)
        {
            settings.applyPersistedSettings(persistedSettings);
            languageList = FXCollections.observableArrayList(settings.getAcceptedLanguageLocales());
            countryList = FXCollections.observableArrayList(settings.getAcceptedCountries());
            arbitratorList = FXCollections.observableArrayList(settings.getAcceptedArbitrators());
        }
        else
        {
            languageList = FXCollections.observableArrayList(new ArrayList<>());
            countryList = FXCollections.observableArrayList(new ArrayList<>());
            arbitratorList = FXCollections.observableArrayList(new ArrayList<>());

            if (Locale.getDefault() != null)
            {
                addLanguage(LanguageUtil.getDefaultLanguageLocale());
                addCountry(CountryUtil.getDefaultCountry());
            }

            // Add english as default as well
            addLanguage(LanguageUtil.getEnglishLanguageLocale());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        super.initialize(url, rb);

        setupGeneralSettingsScreen();
        initBankAccountScreen();
        addMockArbitrator();
    }

    @Override
    public void deactivate()
    {
        super.deactivate();
    }

    @Override
    public void activate()
    {
        super.activate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ViewController loadViewAndGetChildController(NavigationItem navigationItem)
    {
        // TODO
        // caching causes exception
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
        try
        {
            final Node view = loader.load();
            childController = loader.getController();
            childController.setParentController(this);

            final Stage rootStage = BitSquare.getPrimaryStage();
            final Stage stage = new Stage();
            stage.setTitle("Arbitrator selection");
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
            stage.setOnHidden(windowEvent -> {
                if (navigationItem == NavigationItem.ARBITRATOR_OVERVIEW)
                    updateArbitrators();
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
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void updateArbitrators()
    {
        arbitratorList = FXCollections.observableArrayList(settings.getAcceptedArbitrators());
        initArbitrators();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    // General Settings
    @FXML
    public void onAddLanguage()
    {
        addLanguage(languageComboBox.getSelectionModel().getSelectedItem());
        languageComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    public void onSelectRegion()
    {
        countryComboBox.setVisible(true);
        Region selectedRegion = regionComboBox.getSelectionModel().getSelectedItem();
        countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedRegion)));
    }

    @FXML
    public void onAddCountry()
    {
        addCountry(countryComboBox.getSelectionModel().getSelectedItem());
        countryComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    public void onOpenArbitratorScreen()
    {
        loadViewAndGetChildController(NavigationItem.ARBITRATOR_OVERVIEW);
    }


    // Bank Account Settings
    @FXML
    public void selectBankAccount()
    {
        BankAccount bankAccount = bankAccountComboBox.getSelectionModel().getSelectedItem();
        if (bankAccount != null && bankAccount != user.getCurrentBankAccount())
        {
            user.setCurrentBankAccount(bankAccount);
            persistence.write(user);
            fillWithCurrentBankAccount();
        }
    }

    @FXML
    public void selectBankAccountType()
    {
        BankAccountType bankAccountType = bankAccountTypesComboBox.getSelectionModel().getSelectedItem();
        if (bankAccountType != null)
        {
            bankAccountTitleTextField.setText("");
            bankAccountPrimaryIDTextField.setText("");
            bankAccountPrimaryIDTextField.setPromptText(bankAccountType.getPrimaryId());
            bankAccountSecondaryIDTextField.setText("");
            bankAccountSecondaryIDTextField.setPromptText(bankAccountType.getSecondaryId());
        }
    }

    @FXML
    public void onSelectBankAccountRegion()
    {
        bankAccountCountryComboBox.setVisible(true);
        Region selectedBankAccountRegion = bankAccountRegionComboBox.getSelectionModel().getSelectedItem();
        bankAccountCountryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedBankAccountRegion)));
    }

    @FXML
    public void onAddBankAccount()
    {
        saveBankAccount();
        resetBankAccountInput();
    }

    @FXML
    public void onRemoveBankAccount()
    {
        removeBankAccount();
    }

    @FXML
    void onSaveBankAccount()
    {
        saveBankAccount();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // General Settings
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
                    final ImageView icon = ImageUtil.getIconImageView(ImageUtil.REMOVE);

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
                            removeButton.setOnAction(actionEvent -> removeLanguage(item));
                            setGraphic(hBox);
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
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
                    final ImageView icon = ImageUtil.getIconImageView(ImageUtil.REMOVE);

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
                            removeButton.setOnAction(actionEvent -> removeCountry(item));
                            setGraphic(hBox);
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
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
                    final ImageView icon = ImageUtil.getIconImageView(ImageUtil.REMOVE);

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
                            removeButton.setOnAction(actionEvent -> removeArbitrator(item));
                            setGraphic(hBox);
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        arbitratorsListView.setItems(arbitratorList);
    }

    private void addLanguage(Locale locale)
    {
        if (locale != null && !languageList.contains(locale))
        {
            languageList.add(locale);
            settings.addAcceptedLanguageLocale(locale);
        }
    }

    private void removeLanguage(Locale locale)
    {
        languageList.remove(locale);
        settings.removeAcceptedLanguageLocale(locale);
        saveSettings();
    }

    private void addCountry(Country country)
    {
        if (!countryList.contains(country) && country != null)
        {
            countryList.add(country);
            settings.addAcceptedCountry(country);
            saveSettings();
        }
    }

    private void removeCountry(Country country)
    {
        countryList.remove(country);
        settings.removeAcceptedCountry(country);
        saveSettings();
    }

    private void removeArbitrator(Arbitrator arbitrator)
    {
        arbitratorList.remove(arbitrator);
        settings.removeAcceptedArbitrator(arbitrator);
        saveSettings();
    }

    private void saveSettings()
    {
        persistence.write(settings);
    }

    private void saveUser()
    {
        persistence.write(user);
    }


    // Bank Account Settings
    private void fillWithCurrentBankAccount()
    {
        BankAccount currentBankAccount = user.getCurrentBankAccount();
        if (currentBankAccount != null)
        {
            bankAccountTitleTextField.setText(currentBankAccount.getAccountTitle());
            bankAccountHolderNameTextField.setText(currentBankAccount.getAccountHolderName());
            bankAccountPrimaryIDTextField.setText(currentBankAccount.getAccountPrimaryID());
            bankAccountPrimaryIDTextField.setPromptText(currentBankAccount.getBankAccountType().getPrimaryId());
            bankAccountSecondaryIDTextField.setText(currentBankAccount.getAccountSecondaryID());
            bankAccountSecondaryIDTextField.setPromptText(currentBankAccount.getBankAccountType().getSecondaryId());
        }
        else
        {
            resetBankAccountInput();
        }
    }

    private void initBankAccountScreen()
    {
        initBankAccountComboBox();
        initBankAccountTypesComboBox();
        initBankAccountCurrencyComboBox();
        initBankAccountCountryComboBox();

        fillWithCurrentBankAccount();

        //TODO
        if (BitSquare.fillFormsWithDummyData)
        {
            bankAccountTypesComboBox.getSelectionModel().selectFirst();
            bankAccountCurrencyComboBox.getSelectionModel().selectFirst();
            bankAccountRegionComboBox.getSelectionModel().select(3);

            Optional<Country> country = bankAccountCountryComboBox.getItems().stream().filter(e -> e.getCode().equals(CountryUtil.getDefaultCountry().getCode())).findFirst();
            if (country.isPresent())
                bankAccountCountryComboBox.getSelectionModel().select(country.get());

            bankAccountTitleTextField.setText("dummy title");
            bankAccountHolderNameTextField.setText("dummy name");
            bankAccountPrimaryIDTextField.setText("dummy primary ID");
            bankAccountSecondaryIDTextField.setText("dummy secondary ID");

            if (user.getCurrentBankAccount() == null)
                onSaveBankAccount();
        }
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
        if (user.getBankAccounts().isEmpty())
        {
            bankAccountComboBox.setPromptText("No bank account available");
            bankAccountComboBox.setDisable(true);
        }
        else
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
    }

    private void initBankAccountTypesComboBox()
    {
        bankAccountTypesComboBox.setItems(FXCollections.observableArrayList(BankAccountType.getAllBankAccountTypes()));
        bankAccountTypesComboBox.setConverter(new StringConverter<BankAccountType>()
        {
            @Override
            public String toString(BankAccountType bankAccountTypeInfo)
            {
                return Localisation.get(bankAccountTypeInfo.toString());
            }

            @Override
            public BankAccountType fromString(String s)
            {
                return null;
            }
        });

        BankAccount currentBankAccount = user.getCurrentBankAccount();
        if (currentBankAccount != null)
        {
            int index = bankAccountTypesComboBox.getItems().indexOf(currentBankAccount.getBankAccountType());
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
            BankAccount bankAccount = new BankAccount(bankAccountTypesComboBox.getSelectionModel().getSelectedItem(),
                                                      bankAccountCurrencyComboBox.getSelectionModel().getSelectedItem(),
                                                      bankAccountCountryComboBox.getSelectionModel().getSelectedItem(),
                                                      bankAccountTitleTextField.getText(),
                                                      bankAccountHolderNameTextField.getText(),
                                                      bankAccountPrimaryIDTextField.getText(),
                                                      bankAccountSecondaryIDTextField.getText());
            user.addBankAccount(bankAccount);

            saveUser();

            if (!settings.getAcceptedCountries().contains(bankAccount.getCountry()))
            {
                List<Action> actions = new ArrayList<>();
                actions.add(Dialog.Actions.YES);
                actions.add(Dialog.Actions.NO);

                Action response = Popups.openConfirmPopup("Warning",
                                                          "The country of your bank account is not included in the accepted countries in the general settings.\n\nDo you want to add it automatically?",
                                                          null,
                                                          actions);
                if (response == Dialog.Actions.YES)
                    addCountry(bankAccount.getCountry());
            }
        }
    }

    private void removeBankAccount()
    {
        user.removeCurrentBankAccount();

        saveUser();
        saveSettings();
        fillWithCurrentBankAccount();
    }

    private boolean verifyBankAccountData()
    {
        try
        {
            BitSquareValidator.textFieldsNotEmptyWithReset(bankAccountTitleTextField, bankAccountHolderNameTextField, bankAccountPrimaryIDTextField, bankAccountSecondaryIDTextField);

            BankAccountType bankAccountTypeInfo = bankAccountTypesComboBox.getSelectionModel().getSelectedItem();
            BitSquareValidator.textFieldBankAccountPrimaryIDIsValid(bankAccountPrimaryIDTextField, bankAccountTypeInfo);
            BitSquareValidator.textFieldBankAccountSecondaryIDIsValid(bankAccountSecondaryIDTextField, bankAccountTypeInfo);

            return bankAccountTypesComboBox.getSelectionModel().getSelectedItem() != null && bankAccountCountryComboBox.getSelectionModel()
                                                                                                                       .getSelectedItem() != null && bankAccountCurrencyComboBox.getSelectionModel()
                                                                                                                                                                                .getSelectedItem() !=
                    null;
        } catch (BitSquareValidator.ValidationException e)
        {
            return false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Arbitrators
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addMockArbitrator()
    {
        if (settings.getAcceptedArbitrators().isEmpty())
        {
            String pubKeyAsHex = Utils.HEX.encode(new ECKey().getPubKey());
            String messagePubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(user.getMessagePublicKey());
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
                                                   "Bla bla...");

            arbitratorList.add(arbitrator);
            settings.addAcceptedArbitrator(arbitrator);
            persistence.write(settings);

            messageFacade.addArbitrator(arbitrator);
        }
    }


}

