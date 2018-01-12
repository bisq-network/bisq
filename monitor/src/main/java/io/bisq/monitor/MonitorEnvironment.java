/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.monitor;

import io.bisq.common.CommonOptionKeys;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.common.storage.Storage;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BtcOptionKeys;
import io.bisq.core.btc.UserAgent;
import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.network.NetworkOptionKeys;
import joptsimple.OptionSet;
import org.springframework.core.env.JOptCommandLinePropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;

import java.nio.file.Paths;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

public class MonitorEnvironment extends BisqEnvironment {

    private String slackUrlSeedChannel = "";
    private String slackUrlBtcChannel = "";
    private String slackUrlProviderChannel = "";

    public MonitorEnvironment(OptionSet options) {
        this(new JOptCommandLinePropertySource(BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(options)));
    }

    public MonitorEnvironment(PropertySource commandLineProperties) {
        super(commandLineProperties);

        slackUrlSeedChannel = commandLineProperties.containsProperty(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL) ?
                (String) commandLineProperties.getProperty(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL) :
                "";

        slackUrlBtcChannel = commandLineProperties.containsProperty(MonitorOptionKeys.SLACK_BTC_SEED_CHANNEL) ?
                (String) commandLineProperties.getProperty(MonitorOptionKeys.SLACK_BTC_SEED_CHANNEL) :
                "";

        slackUrlProviderChannel = commandLineProperties.containsProperty(MonitorOptionKeys.SLACK_PROVIDER_SEED_CHANNEL) ?
                (String) commandLineProperties.getProperty(MonitorOptionKeys.SLACK_PROVIDER_SEED_CHANNEL) :
                "";

        // hack because defaultProperties() is called from constructor and slackUrlSeedChannel would be null there
        getPropertySources().remove("bisqDefaultProperties");
        getPropertySources().addLast(defaultPropertiesMonitor());
    }

    protected PropertySource<?> defaultPropertiesMonitor() {
        return new PropertiesPropertySource(BISQ_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {
            {
                setProperty(CommonOptionKeys.LOG_LEVEL_KEY, logLevel);
                setProperty(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL, slackUrlSeedChannel);
                setProperty(MonitorOptionKeys.SLACK_BTC_SEED_CHANNEL, slackUrlBtcChannel);
                setProperty(MonitorOptionKeys.SLACK_PROVIDER_SEED_CHANNEL, slackUrlProviderChannel);

                setProperty(NetworkOptionKeys.SEED_NODES_KEY, seedNodes);
                setProperty(NetworkOptionKeys.MY_ADDRESS, myAddress);
                setProperty(NetworkOptionKeys.BAN_LIST, banList);
                setProperty(NetworkOptionKeys.TOR_DIR, Paths.get(btcNetworkDir, "tor").toString());
                setProperty(NetworkOptionKeys.NETWORK_ID, String.valueOf(baseCurrencyNetwork.ordinal()));
                setProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS, socks5ProxyBtcAddress);
                setProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS, socks5ProxyHttpAddress);

                setProperty(AppOptionKeys.APP_DATA_DIR_KEY, appDataDir);
                setProperty(AppOptionKeys.IGNORE_DEV_MSG_KEY, ignoreDevMsg);
                setProperty(AppOptionKeys.DUMP_STATISTICS, dumpStatistics);
                setProperty(AppOptionKeys.APP_NAME_KEY, appName);
                setProperty(AppOptionKeys.MAX_MEMORY, maxMemory);
                setProperty(AppOptionKeys.USER_DATA_DIR_KEY, userDataDir);
                setProperty(AppOptionKeys.PROVIDERS, providers);

                setProperty(DaoOptionKeys.RPC_USER, rpcUser);
                setProperty(DaoOptionKeys.RPC_PASSWORD, rpcPassword);
                setProperty(DaoOptionKeys.RPC_PORT, rpcPort);
                setProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT, rpcBlockNotificationPort);
                setProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA, dumpBlockchainData);
                setProperty(DaoOptionKeys.FULL_DAO_NODE, fullDaoNode);

                setProperty(BtcOptionKeys.BTC_NODES, btcNodes);
                setProperty(BtcOptionKeys.USE_TOR_FOR_BTC, useTorForBtc);
                setProperty(BtcOptionKeys.WALLET_DIR, btcNetworkDir);
                setProperty(BtcOptionKeys.USER_AGENT, userAgent);
                setProperty(BtcOptionKeys.USE_ALL_PROVIDED_NODES, useAllProvidedNodes);
                setProperty(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC, numConnectionForBtc);

                setProperty(UserAgent.NAME_KEY, appName);
                setProperty(UserAgent.VERSION_KEY, Version.VERSION);

                setProperty(Storage.STORAGE_DIR, Paths.get(btcNetworkDir, "db").toString());
                setProperty(KeyStorage.KEY_STORAGE_DIR, Paths.get(btcNetworkDir, "keys").toString());
            }
        });
    }
}
