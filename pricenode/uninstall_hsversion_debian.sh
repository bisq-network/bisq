#!/bin/sh
set -e

echo "[*] Network Size Monitoring removal script"

##### change paths if necessary for your system

ROOT_USER=root

SCRAPER_HOME=/journalreader

#####
echo "[*] Checking environment..."
if [ ! -f "${SCRAPER_HOME}/scraperscript_hsversion.sh" ]; then
	echo 'There is nothing to be removed.'
	echo 'Exiting...'
	exit
fi

echo "[*] Removing journal parser script"
sudo -H -i -u "${ROOT_USER}" rm "${SCRAPER_HOME}/scraperscript_hsversion.sh"

echo "[*] Reverting collectd config"
sudo -H -i -u "${ROOT_USER}" sed -i '/<Plugin exec>.*/ {N;N; s/<Plugin exec>.*scraperscript_hsversion.sh.*<.Plugin>//g}' /etc/collectd/collectd.conf

echo "[*] Restarting services"
sudo -H -i -u "${ROOT_USER}" systemctl restart collectd.service

echo '[*] Done!'
