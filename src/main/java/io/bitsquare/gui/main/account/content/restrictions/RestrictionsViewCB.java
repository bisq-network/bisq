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

package io.bitsquare.gui.main.account.content.restrictions;

import io.bitsquare.BitSquare;
import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.account.MultiStepNavigation;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.Locale;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestrictionsViewCB extends CachedViewCB<RestrictionsPM> implements ContextAware {

    private static final Logger log = LoggerFactory.getLogger(RestrictionsViewCB.class);

    @FXML ListView<Locale> languagesListView;
    @FXML ListView<Country> countriesListView;
    @FXML ListView<Arbitrator> arbitratorsListView;
    @FXML ComboBox<Locale> languageComboBox;
    @FXML ComboBox<Region> regionComboBox;
    @FXML ComboBox<Country> countryComboBox;
    @FXML Button completedButton, addAllEuroCountriesButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private RestrictionsViewCB(RestrictionsPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        initLanguage();
        initCountry();
        initArbitrators();

        completedButton.disableProperty().bind(presentationModel.doneButtonDisable);
    }

    @Override
    public void activate() {
        super.activate();

        languagesListView.setItems(presentationModel.getLanguageList());
        countriesListView.setItems(presentationModel.getCountryList());
        arbitratorsListView.setItems(presentationModel.getArbitratorList());
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ContextAware implementation 
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void useSettingsContext(boolean useSettingsContext) {
        if (useSettingsContext)
            ((GridPane) root).getChildren().remove(completedButton);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    private void onAddLanguage() {
        presentationModel.addLanguage(languageComboBox.getSelectionModel().getSelectedItem());
        languageComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    private void onSelectRegion() {
        countryComboBox.setVisible(true);
        Region region = regionComboBox.getSelectionModel().getSelectedItem();
        countryComboBox.setItems(presentationModel.getAllCountriesFor(region));

        addAllEuroCountriesButton.setVisible(region.getCode().equals("EU"));
    }

    @FXML
    private void onAddCountry() {
        presentationModel.addCountry(countryComboBox.getSelectionModel().getSelectedItem());
        countryComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    private void onAddAllEuroCountries() {
        countriesListView.setItems(presentationModel.getListWithAllEuroCountries());
    }

    @FXML
    private void onOpenArbitratorScreen() {
        loadView(Navigation.Item.ARBITRATOR_BROWSER);
    }


    @FXML
    private void onCompleted() {
        if (parent instanceof MultiStepNavigation)
            ((MultiStepNavigation) parent).nextStep(this);
    }

    @FXML
    private void onOpenLanguagesHelp() {
        Help.openWindow(HelpId.SETUP_RESTRICTION_LANGUAGES);
    }

    @FXML
    private void onOpenCountriesHelp() {
        Help.openWindow(HelpId.SETUP_RESTRICTION_COUNTRIES);
    }

    @FXML
    private void onOpenArbitratorsHelp() {
        Help.openWindow(HelpId.SETUP_RESTRICTION_ARBITRATORS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        // TODO caching causes exception
        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()), false);
        try {
            final Node view = loader.load();
            //TODO Resolve type problem...
            Initializable childController = loader.getController();
            //childController.setParentController(this);

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
                if (navigationItem == Navigation.Item.ARBITRATOR_BROWSER)
                    updateArbitratorList();
            });
            stage.show();

            return childController;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void updateArbitratorList() {
        presentationModel.updateArbitratorList();
        arbitratorsListView.setItems(presentationModel.getArbitratorList());
    }

    private void initLanguage() {
        languagesListView.setCellFactory(new Callback<ListView<Locale>, ListCell<Locale>>() {
            @Override
            public ListCell<Locale> call(ListView<Locale> list) {
                return new ListCell<Locale>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final Locale item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getDisplayName());
                            removeButton.setOnAction(actionEvent -> removeLanguage(item));
                            setGraphic(pane);
                        }
                        else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });

        languageComboBox.setItems(presentationModel.getAllLanguages());
        languageComboBox.setConverter(new StringConverter<Locale>() {
            @Override
            public String toString(Locale locale) {
                return locale.getDisplayLanguage();
            }

            @Override
            public Locale fromString(String s) {
                return null;
            }
        });
    }

    private void initCountry() {
        regionComboBox.setItems(presentationModel.getAllRegions());
        regionComboBox.setConverter(new StringConverter<io.bitsquare.locale.Region>() {
            @Override
            public String toString(io.bitsquare.locale.Region region) {
                return region.getName();
            }

            @Override
            public io.bitsquare.locale.Region fromString(String s) {
                return null;
            }
        });

        countriesListView.setCellFactory(new Callback<ListView<Country>, ListCell<Country>>() {
            @Override
            public ListCell<Country> call(ListView<Country> list) {
                return new ListCell<Country>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final Country item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getName());
                            removeButton.setOnAction(actionEvent -> removeCountry(item));
                            setGraphic(pane);
                        }
                        else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });

        countryComboBox.setConverter(new StringConverter<Country>() {
            @Override
            public String toString(Country country) {
                return country.getName();
            }

            @Override
            public Country fromString(String s) {
                return null;
            }
        });
    }

    private void initArbitrators() {
        arbitratorsListView.setCellFactory(new Callback<ListView<Arbitrator>, ListCell<Arbitrator>>() {
            @Override
            public ListCell<Arbitrator> call(ListView<Arbitrator> list) {
                return new ListCell<Arbitrator>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final Arbitrator item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getName());
                            removeButton.setOnAction(actionEvent -> removeArbitrator(item));
                            setGraphic(pane);
                        }
                        else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }

    private void removeLanguage(Locale locale) {
        presentationModel.removeLanguage(locale);
    }

    private void removeCountry(Country country) {
        presentationModel.removeCountry(country);
    }

    private void removeArbitrator(Arbitrator arbitrator) {
        presentationModel.removeArbitrator(arbitrator);
    }

    

   /* private void addCountry(Country country) {
        if (!countryList.contains(country) && country != null) {
            countryList.add(country);
            settings.addAcceptedCountry(country);
            saveSettings();
        }
    }*/

  /* private void addLanguage(Locale locale) {
        if (locale != null && !languageList.contains(locale)) {
            languageList.add(locale);
            settings.addAcceptedLanguageLocale(locale);
        }
    }*/

}

