#!/bin/bash
set -e

echo "[*] Bisq Server Monitoring installation script"

##### change paths if necessary for your system
BISQ_REPO_URL=https://raw.githubusercontent.com/bisq-network/bisq
BISQ_REPO_TAG=master
ROOT_USER=root
ROOT_GROUP=root
ROOT_HOME=~root
ROOT_PKG=(nginx collectd openssl)

SYSTEMD_ENV_HOME=/etc/default

#####

echo "[*] Gathering information"
read -p "Please provide the onion address of your service (eg. 3f3cu2yw7u457ztq): " onionaddress

echo "[*] Updating apt repo sources"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get update -q

echo "[*] Upgrading OS packages"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get upgrade -qq -y

echo "[*] Installing base packages"
sudo -H -i -u "${ROOT_USER}" DEBIAN_FRONTEND=noninteractive apt-get install -qq -y ${ROOT_PKG[@]}

echo "[*] Preparing Bisq init script for monitoring"
# remove stuff it it is there already
for file in "${SYSTEMD_ENV_HOME}/bisq.env" "${SYSTEMD_ENV_HOME}/bisq-pricenode.env"
do
    if [ -f "$file" ];then
        sudo -H -i -u "${ROOT_USER}" sed -i -e 's/-Dcom.sun.management.jmxremote //g' -e 's/-Dcom.sun.management.jmxremote.local.only=true//g' -e 's/ -Dcom.sun.management.jmxremote.host=127.0.0.1//g' -e 's/ -Dcom.sun.management.jmxremote.port=6969//g' -e 's/ -Dcom.sun.management.jmxremote.rmi.port=6969//g' -e 's/ -Dcom.sun.management.jmxremote.ssl=false//g' -e 's/ -Dcom.sun.management.jmxremote.authenticate=false//g' "${file}"
        sudo -H -i -u "${ROOT_USER}" sed -i -e '/JAVA_OPTS/ s/"$/ -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.local.only=true -Dcom.sun.management.jmxremote.host=127.0.0.1 -Dcom.sun.management.jmxremote.port=6969 -Dcom.sun.management.jmxremote.rmi.port=6969 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"/' "${file}"
    fi
done

echo "[*] Seeding entropy from /dev/urandom"
sudo -H -i -u "${ROOT_USER}" /bin/sh -c "head -1500 /dev/urandom > ${ROOT_HOME}/.rnd"
echo "[*] Installing Nginx config"
sudo -H -i -u "${ROOT_USER}" openssl req -x509 -nodes -newkey rsa:2048 -days 3000 -keyout /etc/nginx/cert.key -out /etc/nginx/cert.crt -subj="/O=Bisq/OU=Bisq Infrastructure/CN=$onionaddress"
curl -s "${BISQ_REPO_URL}/${BISQ_REPO_TAG}/monitor/nginx.conf" > /tmp/nginx.conf
sudo -H -i -u "${ROOT_USER}" install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 /tmp/nginx.conf /etc/nginx/nginx.conf

echo "[*] Installing collectd config"
curl -s "${BISQ_REPO_URL}/${BISQ_REPO_TAG}/monitor/collectd.conf" > /tmp/collectd.conf
sudo -H -i -u "${ROOT_USER}" install -c -o "${ROOT_USER}" -g "${ROOT_GROUP}" -m 644 /tmp/collectd.conf /etc/collectd/collectd.conf
sudo -H -i -u "${ROOT_USER}" sed -i -e "s/__ONION_ADDRESS__/$onionaddress/" /etc/collectd/collectd.conf

echo "[*] Updating systemd daemon configuration"
sudo -H -i -u "${ROOT_USER}" systemctl daemon-reload
sudo -H -i -u "${ROOT_USER}" systemctl enable nginx.service
sudo -H -i -u "${ROOT_USER}" systemctl enable collectd.service

echo "[*] Restarting services"
set +e
service bisq status >/dev/null 2>&1
[ $? != 4 ] && sudo -H -i -u "${ROOT_USER}" systemctl restart bisq.service
service bisq-pricenode status >/dev/null 2>&1
[ $? != 4 ] && sudo -H -i -u "${ROOT_USER}" systemctl restart bisq-pricenode.service
sudo -H -i -u "${ROOT_USER}" systemctl restart nginx.service
sudo -H -i -u "${ROOT_USER}" systemctl restart collectd.service

echo '[*] Done!'

echo '  '
echo '[*] Report this certificate to the monitoring team!'
echo '----------------------------------------------------------------'
echo "Server: $onionaddress"
echo '  '
cat /etc/nginx/cert.crt
echo '----------------------------------------------------------------'
echo '  '
