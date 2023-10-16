#!/bin/sh
echo "[*] Bisq Seednode installation script"

echo "[*] Install Docker"

# Official Installation Guide:  https://docs.docker.com/engine/install/debian/

# Remove conflicting packages
for pkg in docker.io docker-doc docker-compose podman-docker containerd runc; do sudo apt-get remove $pkg; done

# Add Docker's official GPG key:
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Add the repository to Apt sources:
echo \
  "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \
  "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update

sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo groupadd docker
sudo usermod -aG docker $USER

sudo systemctl enable docker
sudo systemctl start docker

mkdir bitcoind_data_dir

sudo docker swarm init
sudo docker stack deploy --compose-file docker-compose.yml bisq-seednode

echo "[*] Adding notes to motd"
ROOT_USER=root
sudo -H -i -u "${ROOT_USER}" sh -c 'echo " " >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "Bisq Seednode instructions:" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "https://github.com/bisq-network/bisq/tree/master/seednode" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo " " >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "How to check logs for Bisq-Seednode service:" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "docker service logs bisq-seednode_bisq-bitcoind" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "docker service logs bisq-seednode_bisq-seednode" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "docker service logs bisq-seednode_bisq-tor" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo " " >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "How to restart Bisq-Seednode service:" >> /etc/motd'
sudo -H -i -u "${ROOT_USER}" sh -c 'echo "systemctl restart docker" >> /etc/motd'
