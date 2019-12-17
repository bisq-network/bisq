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

package bisq.core.app;

import bisq.core.btc.BtcOptionKeys;
import bisq.core.dao.DaoOptionKeys;

import bisq.common.BisqException;

import org.springframework.core.env.JOptCommandLinePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import joptsimple.OptionSet;

import java.util.Properties;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BisqEnvironment extends StandardEnvironment {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final String BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME = "bisqCommandLineProperties";
    public static final String BISQ_DEFAULT_PROPERTY_SOURCE_NAME = "bisqDefaultProperties";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    protected final String userAgent;
    @Getter
    @Setter
    protected boolean isBitcoinLocalhostNodeRunning;

    protected final String rpcUser, rpcPassword,
            rpcHost, rpcPort, rpcBlockNotificationPort, rpcBlockNotificationHost, dumpBlockchainData, fullDaoNode,
            useAllProvidedNodes, numConnectionForBtc, genesisTxId, genesisBlockHeight, genesisTotalSupply,
            daoActivated;

    public BisqEnvironment(OptionSet options) {
        this(new JOptCommandLinePropertySource(BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(
                options)));
    }

    @SuppressWarnings("ConstantConditions")
    public BisqEnvironment(PropertySource commandLineProperties) {
        //DaoOptionKeys
        rpcUser = getProperty(commandLineProperties, DaoOptionKeys.RPC_USER, "");
        rpcPassword = getProperty(commandLineProperties, DaoOptionKeys.RPC_PASSWORD, "");
        rpcHost = getProperty(commandLineProperties, DaoOptionKeys.RPC_HOST, "");
        rpcPort = getProperty(commandLineProperties, DaoOptionKeys.RPC_PORT, "");
        rpcBlockNotificationPort = getProperty(commandLineProperties, DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT, "");
        rpcBlockNotificationHost = getProperty(commandLineProperties, DaoOptionKeys.RPC_BLOCK_NOTIFICATION_HOST, "");
        dumpBlockchainData = getProperty(commandLineProperties, DaoOptionKeys.DUMP_BLOCKCHAIN_DATA, "");
        fullDaoNode = getProperty(commandLineProperties, DaoOptionKeys.FULL_DAO_NODE, "");
        genesisTxId = getProperty(commandLineProperties, DaoOptionKeys.GENESIS_TX_ID, "");
        genesisBlockHeight = getProperty(commandLineProperties, DaoOptionKeys.GENESIS_BLOCK_HEIGHT, "-1");
        genesisTotalSupply = getProperty(commandLineProperties, DaoOptionKeys.GENESIS_TOTAL_SUPPLY, "-1");
        daoActivated = getProperty(commandLineProperties, DaoOptionKeys.DAO_ACTIVATED, "true");

        //BtcOptionKeys
        userAgent = getProperty(commandLineProperties, BtcOptionKeys.USER_AGENT, "Bisq");
        useAllProvidedNodes = getProperty(commandLineProperties, BtcOptionKeys.USE_ALL_PROVIDED_NODES, "false");
        numConnectionForBtc = getProperty(commandLineProperties, BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC, "9");

        MutablePropertySources propertySources = getPropertySources();
        propertySources.addFirst(commandLineProperties);
        try {
            propertySources.addLast(defaultProperties());
        } catch (Exception ex) {
            throw new BisqException(ex);
        }
    }

    private String getProperty(PropertySource properties, String propertyKey, String defaultValue) {
        return properties.containsProperty(propertyKey) ? (String) properties.getProperty(propertyKey) : defaultValue;
    }

    private PropertySource<?> defaultProperties() {
        return new PropertiesPropertySource(BISQ_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {
            {
                setProperty(DaoOptionKeys.RPC_USER, rpcUser);
                setProperty(DaoOptionKeys.RPC_PASSWORD, rpcPassword);
                setProperty(DaoOptionKeys.RPC_HOST, rpcHost);
                setProperty(DaoOptionKeys.RPC_PORT, rpcPort);
                setProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT, rpcBlockNotificationPort);
                setProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_HOST, rpcBlockNotificationHost);
                setProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA, dumpBlockchainData);
                setProperty(DaoOptionKeys.FULL_DAO_NODE, fullDaoNode);
                setProperty(DaoOptionKeys.GENESIS_TX_ID, genesisTxId);
                setProperty(DaoOptionKeys.GENESIS_BLOCK_HEIGHT, genesisBlockHeight);
                setProperty(DaoOptionKeys.GENESIS_TOTAL_SUPPLY, genesisTotalSupply);
                setProperty(DaoOptionKeys.DAO_ACTIVATED, daoActivated);

                setProperty(BtcOptionKeys.USER_AGENT, userAgent);
                setProperty(BtcOptionKeys.USE_ALL_PROVIDED_NODES, useAllProvidedNodes);
                setProperty(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC, numConnectionForBtc);
            }
        });
    }
}
