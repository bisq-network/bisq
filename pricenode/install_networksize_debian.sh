#!/bin/sh
set -e

echo "[*] Network Size Monitoring installation script"

##### change paths if necessary for your system

ROOT_USER=root

SCRAPER_HOME=/journalreader
SCRAPER_USER=journalreader
SCRAPER_GROUP=systemd-journal

#####
echo "[*] Checking environment..."
if [ ! -f "/etc/collectd/collectd.conf" ]; then
	echo 'Collectd is not installed. Did you do the install_monitoring_debian.sh?'
	echo 'Exiting...'
	exit
fi

echo "[*] Creating journal reader user"
sudo -H -i -u "${ROOT_USER}" useradd -d "${SCRAPER_HOME}" -G "${SCRAPER_GROUP}" "${SCRAPER_USER}"
sudo -H -i -u "${ROOT_USER}" mkdir -p "${SCRAPER_HOME}"
sudo -H -i -u "${ROOT_USER}" chown "${SCRAPER_USER}":"${SCRAPER_GROUP}" ${SCRAPER_HOME}

echo "[*] Installing journal parser script"
curl -s https://raw.githubusercontent.com/bisq-network/bisq/master/pricenode/journalscraper.sh > /tmp/journalscraper.sh
sudo -H -i -u "${ROOT_USER}" install -c -o "${SCRAPER_USER}" -g "${SCRAPER_GROUP}" -m 744 /tmp/journalscraper.sh "${SCRAPER_HOME}/scraperscript.sh"

echo "[*] Installing collectd config"
curl -s https://raw.githubusercontent.com/bisq-network/bisq/master/pricenode/collectd.conf.snippet > /tmp/collectd.conf.snippet
sudo -H -i -u "${ROOT_USER}" /bin/sh -c "cat /tmp/collectd.conf.snippet >> /etc/collectd/collectd.conf"
sudo -H -i -u "${ROOT_USER}" sed -i -e "s/__USER_GROUP__/${SCRAPER_USER}:${SCRAPER_GROUP}/" /etc/collectd/collectd.conf
sudo -H -i -u "${ROOT_USER}" sed -i -e "s!__SCRAPERSCRIPT__!${SCRAPER_HOME}/scraperscript.sh!" /etc/collectd/collectd.conf

sudo -H -i -u "${ROOT_USER}" systemctl enable collectd.service

echo "[*] Restarting services"
sudo -H -i -u "${ROOT_USER}" systemctl restart collectd.service

echo '[*] Done!'
