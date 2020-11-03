#!/usr/bin/env bash
set -e

echo "[*] Bisq bisq-pricenode installation script"

##### change as necessary for your system

SYSTEMD_SERVICE_HOME=/etc/systemd/system
SYSTEMD_ENV_HOME=/etc/default

ROOT_USER=root
ROOT_GROUP=root
#ROOT_HOME=/root

BISQ_USER=bisq
BISQ_GROUP=bisq
BISQ_HOME=/bisq

BISQ_REPO_URL=https://github.com/bisq-network/bisq
BISQ_REPO_NAME=bisq
BISQ_REPO_TAG=master
BISQ_LATEST_RELEASE=master
BISQ_TORHS=pricenode

TOR_PKG="tor"
#TOR_USER=debian-tor
TOR_GROUP=debian-tor
TOR_CONF=/etc/tor/torrc
TOR_RESOURCES=/var/lib/tor

#####

echo "[*] Upgrading apt packages"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get update -q
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get upgrade -qq -y

echo "[*] Installing Git LFS"
sudo -H -i -u "${ROOT_USER}" curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.deb.sh | bash
sudo -H -i -u "${ROOT_USER}" apt-get -y install git-lfs
sudo -H -i -u "${ROOT_USER}" git lfs install

echo "[*] Installing Tor"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get install -qq -y "${TOR_PKG}"

echo "[*] Adding Tor configuration"
if ! grep "${BISQ_TORHS}" /etc/tor/torrc >/dev/null 2>&1;then
  sudo -H -i -u "${ROOT_USER}" sh -c "echo HiddenServiceDir ${TOR_RESOURCES}/${BISQ_TORHS}/ >> ${TOR_CONF}"
  sudo -H -i -u "${ROOT_USER}" sh -c "echo HiddenServicePort 80 127.0.0.1:8080 >> ${TOR_CONF}"
  sudo -H -i -u "${ROOT_USER}" sh -c "echo HiddenServiceVersion 3 >> ${TOR_CONF}"
fi

echo "[*] Creating Bisq user with Tor access"
sudo -H -i -u "${ROOT_USER}" useradd -d "${BISQ_HOME}" -G "${TOR_GROUP}" "${BISQ_USER}"

echo "[*] Creating Bisq homedir"
sudo -H -i -u "${ROOT_USER}" mkdir -p "${BISQ_HOME}"
sudo -H -i -u "${ROOT_USER}" chown "${BISQ_USER}":"${BISQ_GROUP}" ${BISQ_HOME}

echo "[*] Cloning Bisq repo"
sudo -H -i -u "${BISQ_USER}" git config --global advice.detachedHead false
sudo -H -i -u "${BISQ_USER}" git clone --branch "${BISQ_REPO_TAG}" "${BISQ_REPO_URL}" "${BISQ_HOME}/${BISQ_REPO_NAME}"

echo "[*] Installing OpenJDK 11"
sudo -H -i -u "${ROOT_USER}" apt-get install -qq -y openjdk-11-jdk

echo "[*] Checking out Bisq ${BISQ_LATEST_RELEASE}"
sudo -H -i -u "${BISQ_USER}" sh -c "cd ${BISQ_HOME}/${BISQ_REPO_NAME} && git checkout ${BISQ_LATEST_RELEASE}"

echo "[*] Performing Git LFS pull"
sudo -H -i -u "${BISQ_USER}" sh -c "cd ${BISQ_HOME}/${BISQ_REPO_NAME} && git lfs pull"

echo "[*] Building Bisq from source"
sudo -H -i -u "${BISQ_USER}" sh -c "cd ${BISQ_HOME}/${BISQ_REPO_NAME} && ./gradlew :pricenode:installDist  -x test < /dev/null" # redirect from /dev/null is necessary to workaround gradlew non-interactive shell hanging issue

echo "[*] Installing bisq-pricenode systemd service"
sudo -H -i -u "${ROOT_USER}" install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 "${BISQ_HOME}/${BISQ_REPO_NAME}/pricenode/bisq-pricenode.service" "${SYSTEMD_SERVICE_HOME}"
sudo -H -i -u "${ROOT_USER}" install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 "${BISQ_HOME}/${BISQ_REPO_NAME}/pricenode/bisq-pricenode.env" "${SYSTEMD_ENV_HOME}"

echo "[*] Reloading systemd daemon configuration"
sudo -H -i -u "${ROOT_USER}" systemctl daemon-reload

echo "[*] Enabling bisq-pricenode service"
sudo -H -i -u "${ROOT_USER}" systemctl enable bisq-pricenode.service

echo "[*] Starting bisq-pricenode service"
sudo -H -i -u "${ROOT_USER}" systemctl start bisq-pricenode.service
sleep 5
sudo -H -i -u "${ROOT_USER}" journalctl --no-pager --unit bisq-pricenode

echo "[*] Restarting Tor"
sudo -H -i -u "${ROOT_USER}" service tor restart
sleep 5

echo '[*] Done!'
echo -n '[*] Access your pricenode at http://'
cat "${TOR_RESOURCES}/${BISQ_TORHS}/hostname"

exit 0
