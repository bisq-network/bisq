package io.bitsquare.trade.orderbook;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.gui.market.orderbook.OrderBookListItem;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OrderBookListener;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trading;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.User;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javax.inject.Inject;
import net.tomp2p.peers.Number640;
import net.tomp2p.storage.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
TODO
remove dependencies to tomp2p
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;
 */
public class OrderBook implements OrderBookListener
{
    private static final Logger log = LoggerFactory.getLogger(OrderBook.class);
    private final ObservableList<OrderBookListItem> allOffers = FXCollections.observableArrayList();
    private final FilteredList<OrderBookListItem> filteredList = new FilteredList<>(allOffers);
    // FilteredList does not support sorting, so we need to wrap it to a SortedList
    private final SortedList<OrderBookListItem> offerList = new SortedList<>(filteredList);
    private final Settings settings;
    private final User user;
    private final MessageFacade messageFacade;
    private final Trading trading;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OrderBook(Settings settings, User user, MessageFacade messageFacade, Trading trading)
    {
        this.settings = settings;
        this.user = user;
        this.messageFacade = messageFacade;
        this.trading = trading;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init()
    {
        messageFacade.addOrderBookListener(this);
    }

    public void cleanup()
    {
        messageFacade.removeOrderBookListener(this);
    }

    public void loadOffers()
    {
        if (user.getCurrentBankAccount() != null)
        {
            messageFacade.getOffers(user.getCurrentBankAccount().getCurrency().getCurrencyCode());
        }
        else
        {
            messageFacade.getOffers(CurrencyUtil.getDefaultCurrency().getCurrencyCode());
        }
    }

    public void removeOffer(Offer offer)
    {
        trading.removeOffer(offer);
    }

    public void applyFilter(OrderBookFilter orderBookFilter)
    {
        filteredList.setPredicate(orderBookListItem -> {
            Offer offer = orderBookListItem.getOffer();
            BankAccount currentBankAccount = user.getCurrentBankAccount();

            if (orderBookFilter == null || currentBankAccount == null || orderBookFilter.getDirection() == null)
            {
                return false;
            }

            // The users current bank account currency must match the offer currency (1 to 1)
            boolean currencyResult = currentBankAccount.getCurrency().equals(offer.getCurrency());

            // The offer bank account country must match one of the accepted countries defined in the settings (1 to n)
            boolean countryResult = countryInList(offer.getBankAccountCountry(), settings.getAcceptedCountries());

            // One of the supported languages from the settings must match one of the offer languages (n to n)
            boolean languageResult = languagesInList(settings.getAcceptedLanguageLocales(), offer.getAcceptedLanguageLocales());

            // Apply applyFilter only if there is a valid value set
            // The requested amount must be lower or equal then the offer amount
            boolean amountResult = true;
            if (orderBookFilter.getAmount() > 0)
            {
                //TODO
                amountResult = orderBookFilter.getAmount() <= offer.getAmount().value;
            }

            // The requested trade direction must be opposite of the offerList trade direction
            boolean directionResult = !orderBookFilter.getDirection().equals(offer.getDirection());

            // Apply applyFilter only if there is a valid value set
            boolean priceResult = true;
            if (orderBookFilter.getPrice() > 0)
            {
                if (offer.getDirection() == Direction.SELL)
                {
                    priceResult = orderBookFilter.getPrice() >= offer.getPrice();
                }
                else
                {
                    priceResult = orderBookFilter.getPrice() <= offer.getPrice();
                }
            }

            // The arbitrator defined in the offer must match one of the accepted arbitrators defined in the settings (1 to n)
            boolean arbitratorResult = arbitratorInList(offer.getArbitrator(), settings.getAcceptedArbitrators());


            //noinspection UnnecessaryLocalVariable
            boolean result = currencyResult && countryResult && languageResult && amountResult && directionResult && priceResult && arbitratorResult;

                  /*
            log.debug("result = " + result +
                    ", currencyResult = " + currencyResult +
                    ", countryResult = " + countryResult +
                    ", languageResult = " + languageResult +
                    ", amountResult = " + amountResult +
                    ", directionResult = " + directionResult +
                    ", priceResult = " + priceResult +
                    ", arbitratorResult = " + arbitratorResult
            );

            log.debug("currentBankAccount.getCurrency() = " + currentBankAccount.getCurrency() +
                    ", offer.getCurrency() = " + offer.getCurrency());
            log.debug("offer.getCountryLocale() = " + offer.getBankAccountCountryLocale() +
                    ", settings.getAcceptedCountries() = " + settings.getAcceptedCountries().toString());
            log.debug("settings.getAcceptedLanguageLocales() = " + settings.getAcceptedLanguageLocales() +
                    ", offer.getAcceptedLanguageLocales() = " + offer.getAcceptedLanguageLocales());
            log.debug("currentBankAccount.getBankAccountType().getType() = " + currentBankAccount.getBankAccountType().getType() +
                    ", offer.getBankAccountTypeEnum() = " + offer.getBankAccountTypeEnum());
            log.debug("orderBookFilter.getAmount() = " + orderBookFilter.getAmount() +
                    ", offer.getAmount() = " + offer.getAmount());
            log.debug("orderBookFilter.getDirection() = " + orderBookFilter.getDirection() +
                    ", offer.getDirection() = " + offer.getDirection());
            log.debug("orderBookFilter.getPrice() = " + orderBookFilter.getPrice() +
                    ", offer.getPrice() = " + offer.getPrice());
            log.debug("offer.getArbitrator() = " + offer.getArbitrator() +
                    ", settings.getAcceptedArbitrators() = " + settings.getAcceptedArbitrators());
                     */
            return result;
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: MessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onOfferAdded(Data offerData, boolean success)
    {
        try
        {
            Object offerDataObject = offerData.object();
            if (offerDataObject instanceof Offer)
            {
                Offer offer = (Offer) offerDataObject;
                allOffers.add(new OrderBookListItem(offer));
            }
        } catch (ClassNotFoundException | IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    @Override
    public void onOffersReceived(Map<Number640, Data> dataMap, boolean success)
    {
        if (success && dataMap != null)
        {
            allOffers.clear();

            for (Data offerData : dataMap.values())
            {
                try
                {
                    Object offerDataObject = offerData.object();
                    if (offerDataObject instanceof Offer)
                    {
                        Offer offer = (Offer) offerDataObject;
                        OrderBookListItem orderBookListItem = new OrderBookListItem(offer);
                        allOffers.add(orderBookListItem);
                    }
                } catch (ClassNotFoundException | IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        else
        {
            allOffers.clear();
        }
    }

    @Override
    public void onOfferRemoved(Data offerData, boolean success)
    {
        if (success && offerData != null)
        {
            try
            {
                Object offerDataObject = offerData.object();
                if (offerDataObject instanceof Offer)
                {
                    Offer offer = (Offer) offerDataObject;
                    allOffers.removeIf(orderBookListItem -> orderBookListItem.getOffer().getId().equals(offer.getId()));
                }
            } catch (ClassNotFoundException | IOException e)
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        else
        {
            log.warn("onOfferRemoved failed");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////


    public SortedList<OrderBookListItem> getOfferList()
    {
        return offerList;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean countryInList(Country countryToMatch, List<Country> list)
    {
        for (Country country : list)
        {
            if (country.getCode().equals(countryToMatch.getCode()))
            {
                return true;
            }
        }
        return false;
    }

    private boolean languagesInList(List<Locale> list1, List<Locale> list2)
    {
        for (Locale locale1 : list2)
        {
            for (Locale locale2 : list1)
            {
                if (locale1.getLanguage().equals(locale2.getLanguage()))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean arbitratorInList(Arbitrator arbitratorToMatch, List<Arbitrator> list)
    {
        if (arbitratorToMatch != null)
        {
            for (Arbitrator arbitrator : list)
            {
                try
                {
                    if (arbitrator.getId().equals(arbitratorToMatch.getId()))
                    {
                        return true;
                    }
                } catch (Exception e)
                {
                    log.error(e.toString());
                }
            }
        }
        return false;
    }


}
