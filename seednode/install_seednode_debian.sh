#!/bin/sh
set -e

echo "[*] Bisq Seednode installation script"

##### change paths if necessary for your system

ROOT_USER=root
ROOT_GROUP=root
ROOT_PKG="build-essential libtool autotools-dev automake pkg-config bsdmainutils python3 git vim screen ufw openjdk-11-jdk"
ROOT_HOME=/root

SYSTEMD_SERVICE_HOME=/etc/systemd/system
SYSTEMD_ENV_HOME=/etc/default

BISQ_REPO_URL=https://github.com/bisq-network/bisq
BISQ_REPO_NAME=bisq
BISQ_REPO_TAG=master
BISQ_LATEST_RELEASE=$(curl -s https://api.github.com/repos/bisq-network/bisq/releases/latest|grep tag_name|head -1|cut -d '"' -f4)
BISQ_HOME=/bisq
BISQ_USER=bisq

# by default, this script will build and setup bitcoin fullnode
# if you want to use an existing bitcoin fullnode, see next section
BITCOIN_INSTALL=true
BITCOIN_REPO_URL=https://github.com/bitcoin/bitcoin
BITCOIN_REPO_NAME=bitcoin
BITCOIN_REPO_TAG=$(curl -s https://api.github.com/repos/bitcoin/bitcoin/releases/latest|grep tag_name|head -1|cut -d '"' -f4)
BITCOIN_HOME=/bitcoin
BITCOIN_USER=bitcoin
BITCOIN_GROUP=bitcoin
BITCOIN_PKG="libevent-dev libboost-system-dev libboost-filesystem-dev libboost-chrono-dev libboost-test-dev libboost-thread-dev libdb-dev libssl-dev"
BITCOIN_P2P_HOST=127.0.0.1
BITCOIN_P2P_PORT=8333
BITCOIN_RPC_HOST=127.0.0.1
BITCOIN_RPC_PORT=8332
BITCOIN_RPC_BLOCKNOTIFY_HOST=127.0.0.1
BITCOIN_RPC_BLOCKNOTIFY_PORT=5120

# set below settings to use existing bitcoin node
#BITCOIN_INSTALL=false
#BITCOIN_P2P_HOST=192.168.1.1
#BITCOIN_P2P_PORT=8333
#BITCOIN_RPC_HOST=192.168.1.1
#BITCOIN_RPC_PORT=8332
#BITCOIN_RPC_USER=foo
#BITCOIN_RPC_PASS=bar
#BITCOIN_RPC_BLOCKNOTIFY_HOST=0.0.0.0
#BITCOIN_RPC_BLOCKNOTIFY_PORT=5120

TOR_PKG="tor"
TOR_USER=debian-tor
TOR_GROUP=debian-tor
TOR_HOME=/etc/tor

#####

echo "[*] Updating apt repo sources"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get update -q

echo "[*] Upgrading OS packages"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get upgrade -qq -y

echo "[*] Installing base packages"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get install -qq -y ${ROOT_PKG}

echo "[*] Cloning Bisq repo"
sudo -H -i -u "${ROOT_USER}" git config --global advice.detachedHead false
sudo -H -i -u "${ROOT_USER}" git clone --branch "${BISQ_REPO_TAG}" "${BISQ_REPO_URL}" "${ROOT_HOME}/${BISQ_REPO_NAME}"

echo "[*] Installing Tor"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get update -q
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get install -qq -y ${TOR_PKG}

echo "[*] Installing Tor configuration"
sudo -H -i -u "${ROOT_USER}" install -c -m 644 "${ROOT_HOME}/${BISQ_REPO_NAME}/seednode/torrc" "${TOR_HOME}/torrc"

if [ "${BITCOIN_INSTALL}" = true ];then

	echo "[*] Creating Bitcoin user with Tor access"
	sudo -H -i -u "${ROOT_USER}" useradd -d "${BITCOIN_HOME}" -G "${TOR_GROUP}" "${BITCOIN_USER}"

	echo "[*] Installing Bitcoin build dependencies"
	sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get install -qq -y ${BITCOIN_PKG}

	echo "[*] Creating Bitcoin homedir"
	sudo -H -i -u "${ROOT_USER}" mkdir -p "${BITCOIN_HOME}"
	sudo -H -i -u "${ROOT_USER}" chown "${BITCOIN_USER}":"${BITCOIN_GROUP}" ${BITCOIN_HOME}
	sudo -H -i -u "${BITCOIN_USER}" ln -s . .bitcoin

	echo "[*] Cloning Bitcoin repo"
	sudo -H -i -u "${BITCOIN_USER}" git config --global advice.detachedHead false
	sudo -H -i -u "${BITCOIN_USER}" git clone --branch "${BITCOIN_REPO_TAG}" "${BITCOIN_REPO_URL}" "${BITCOIN_HOME}/${BITCOIN_REPO_NAME}"

	echo "[*] Building Bitcoin from source"
	sudo -H -i -u "${BITCOIN_USER}" sh -c "cd ${BITCOIN_REPO_NAME} && ./autogen.sh --quiet && ./configure --quiet --disable-wallet --with-incompatible-bdb && make -j9"

	echo "[*] Installing Bitcoin into OS"
	sudo -H -i -u "${ROOT_USER}" sh -c "cd ${BITCOIN_HOME}/${BITCOIN_REPO_NAME} && make install >/dev/null"

	echo "[*] Installing Bitcoin configuration"
	sudo -H -i -u "${ROOT_USER}" install -c -o "${BITCOIN_USER}" -g "${BITCOIN_GROUP}" -m 644 "${ROOT_HOME}/${BISQ_REPO_NAME}/seednode/bitcoin.conf" "${BITCOIN_HOME}/bitcoin.conf"
	sudo -H -i -u "${ROOT_USER}" install -c -o "${BITCOIN_USER}" -g "${BITCOIN_GROUP}" -m 755 "${ROOT_HOME}/${BISQ_REPO_NAME}/seednode/blocknotify.sh" "${BITCOIN_HOME}/blocknotify.sh"

	echo "[*] Generating Bitcoin RPC credentials"
	BITCOIN_RPC_USER=$(head -150 /dev/urandom | md5sum | awk '{print $1}')
	sudo sed -i -e "s/__BITCOIN_RPC_USER__/${BITCOIN_RPC_USER}/" "${BITCOIN_HOME}/bitcoin.conf"
	BITCOIN_RPC_PASS=$(head -150 /dev/urandom | md5sum | awk '{print $1}')
	sudo sed -i -e "s/__BITCOIN_RPC_PASS__/${BITCOIN_RPC_PASS}/" "${BITCOIN_HOME}/bitcoin.conf"

	echo "[*] Installing Bitcoin init scripts"
	sudo -H -i -u "${ROOT_USER}" install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 "${ROOT_HOME}/${BISQ_REPO_NAME}/seednode/bitcoin.service" "${SYSTEMD_SERVICE_HOME}"

fi

echo "[*] Creating Bisq user with Tor access"
sudo -H -i -u "${ROOT_USER}" useradd -d "${BISQ_HOME}" -G "${TOR_GROUP}" "${BISQ_USER}"

echo "[*] Creating Bisq homedir"
sudo -H -i -u "${ROOT_USER}" mkdir -p "${BISQ_HOME}"
sudo -H -i -u "${ROOT_USER}" chown "${BISQ_USER}":"${BISQ_GROUP}" ${BISQ_HOME}

echo "[*] Moving Bisq repo"
sudo -H -i -u "${ROOT_USER}" mv "${ROOT_HOME}/${BISQ_REPO_NAME}" "${BISQ_HOME}/${BISQ_REPO_NAME}"
sudo -H -i -u "${ROOT_USER}" chown -R "${BISQ_USER}:${BISQ_GROUP}" "${BISQ_HOME}/${BISQ_REPO_NAME}"

echo "[*] Installing Bisq init script"
sudo -H -i -u "${ROOT_USER}" install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 "${BISQ_HOME}/${BISQ_REPO_NAME}/seednode/bisq.service" "${SYSTEMD_SERVICE_HOME}/bisq.service"
if [ "${BITCOIN_INSTALL}" = true ];then
    sudo sed -i -e "s/After=network.target/After=bitcoin.service/" "${SYSTEMD_SERVICE_HOME}/bisq.service"
	sudo sed -i -e "s/#Requires=bitcoin.service/Requires=bitcoin.service/" "${SYSTEMD_SERVICE_HOME}/bisq.service"
	sudo sed -i -e "s/#BindsTo=bitcoin.service/BindsTo=bitcoin.service/" "${SYSTEMD_SERVICE_HOME}/bisq.service"
fi
sudo sed -i -e "s/__BISQ_REPO_NAME__/${BISQ_REPO_NAME}/" "${SYSTEMD_SERVICE_HOME}/bisq.service"
sudo sed -i -e "s!__BISQ_HOME__!${BISQ_HOME}!" "${SYSTEMD_SERVICE_HOME}/bisq.service"

echo "[*] Generating ECDSA key for BM oracle node"
key=$(openssl ecparam -name secp256k1 -genkey)
# Extract the private key in hex format
BISQ_BM_ORACLE_NODE_PRIVKEY=$(echo "$key" | openssl ec -text -noout 2>/dev/null | awk '/priv:/{flag=1;next}/pub:/{flag=0}flag' | tr -d ' \n:')
# Extract the compressed public key in hex format
BISQ_BM_ORACLE_NODE_PUBKEY=$(echo "$key" | openssl ec -pubout -conv_form compressed -outform DER 2>/dev/null | tail -c 33 | xxd -p | tr -d '\n')

echo "[*] Installing Bisq environment file"
sudo -H -i -u "${ROOT_USER}" install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 "${BISQ_HOME}/${BISQ_REPO_NAME}/seednode/bisq.env" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s/__BITCOIN_P2P_HOST__/${BITCOIN_P2P_HOST}/" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s/__BITCOIN_P2P_PORT__/${BITCOIN_P2P_PORT}/" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s/__BITCOIN_RPC_HOST__/${BITCOIN_RPC_HOST}/" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s/__BITCOIN_RPC_PORT__/${BITCOIN_RPC_PORT}/" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s/__BITCOIN_RPC_USER__/${BITCOIN_RPC_USER}/" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s/__BITCOIN_RPC_PASS__/${BITCOIN_RPC_PASS}/" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s/__BITCOIN_RPC_BLOCKNOTIFY_HOST__/${BITCOIN_RPC_BLOCKNOTIFY_HOST}/" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s/__BITCOIN_RPC_BLOCKNOTIFY_PORT__/${BITCOIN_RPC_BLOCKNOTIFY_PORT}/" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s!__BISQ_APP_NAME__!${BISQ_APP_NAME}!" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s!__BISQ_HOME__!${BISQ_HOME}!" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s!__BISQ_BM_ORACLE_NODE_PUBKEY__!${BISQ_BM_ORACLE_NODE_PUBKEY}!" "${SYSTEMD_ENV_HOME}/bisq.env"
sudo sed -i -e "s!__BISQ_BM_ORACLE_NODE_PRIVKEY__!${BISQ_BM_ORACLE_NODE_PRIVKEY}!" "${SYSTEMD_ENV_HOME}/bisq.env"

echo "[*] Checking out Bisq ${BISQ_LATEST_RELEASE}"
sudo -H -i -u "${BISQ_USER}" sh -c "cd ${BISQ_HOME}/${BISQ_REPO_NAME} && git checkout ${BISQ_LATEST_RELEASE}"

echo "[*] Building Bisq from source"
sudo -H -i -u "${BISQ_USER}" sh -c "cd ${BISQ_HOME}/${BISQ_REPO_NAME} && ./gradlew build -x test < /dev/null" # redirect from /dev/null is necessary to workaround gradlew non-interactive shell hanging issue

echo "[*] Updating systemd daemon configuration"
sudo -H -i -u "${ROOT_USER}" systemctl daemon-reload
sudo -H -i -u "${ROOT_USER}" systemctl enable tor.service
sudo -H -i -u "${ROOT_USER}" systemctl enable bisq.service
if [ "${BITCOIN_INSTALL}" = true ];then
	sudo -H -i -u "${ROOT_USER}" systemctl enable bitcoin.service
fi

echo "[*] Preparing firewall"
sudo -H -i -u "${ROOT_USER}" ufw default deny incoming
sudo -H -i -u "${ROOT_USER}" ufw default allow outgoing

echo "[*] Starting Tor"
sudo -H -i -u "${ROOT_USER}" systemctl start tor

if [ "${BITCOIN_INSTALL}" = true ];then
	echo "[*] Starting Bitcoin"
	sudo -H -i -u "${ROOT_USER}" systemctl start bitcoin
	sudo -H -i -u "${ROOT_USER}" journalctl --no-pager --unit bitcoin
	sudo -H -i -u "${ROOT_USER}" tail "${BITCOIN_HOME}/debug.log"
fi

echo "[*] Adding notes to motd"
sudo -H -i -u "${ROOT_USER}" sh -c 'echo " " >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "Bisq Seednode instructions:" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "https://github.com/bisq-network/bisq/tree/master/seednode" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo " " >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "How to check logs for Bisq-Seednode service:" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "sudo journalctl --no-pager --unit bisq" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo " " >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "How to restart Bisq-Seednode service:" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "sudo service bisq restart" >> /etc/motd'

echo '[*] Done!'

echo '  '
echo '[*] DONT FORGET TO ENABLE FIREWALL!!!11'
echo '[*] Follow all the README instructions!'
echo '  '
