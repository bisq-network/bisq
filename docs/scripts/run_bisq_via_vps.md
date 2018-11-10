## Hosted Bisq using VPS


From: https://github.com/bisq-network/exchange/issues/511

It's possible to install and view Bisq using a Google Compute Engine instance. This is handy and allows offers to always be visible, as well as the possibility to inspect the status of the client.

This is how to host Bisq:

Go to Google compute engine (https://cloud.google.com/compute/) and start an instance using the free trial. One gets $300 credit and 60 days trial period. I used one CPU, 40 GByte of boot disk, and the default value of RAM, 3.75 GB.
I chose Ubuntu 16.04 LTS as the operating system.

How to make a project and start an instance is explained in: https://goo.gl/1Ljy7O which also explains how to install a vnc-server. In short, connect via ssh to your instance, which can be done from the Google compute engine (gce) console, then execute the following script:

```
sudo apt-get -y update;
sudo apt-get -y install tightvncserver;
sudo apt-get -y install aptitude tasksel
sudo tasksel install gnome-desktop --new-install;
sudo apt-get -y install gnome-panel gnome-settings-daemon metacity nautilus gnome-terminal;
sudo apt-get -y install ubuntu-desktop;
sudo apt-get -y install gnome-session-fallback;
mkdir .vnc;

echo "#!/bin/sh
[ -x /etc/vnc/xstartup ] && exec /etc/vnc/xstartup
[ -r $HOME/.Xresources ] && xrdb $HOME/.Xresources
xsetroot -solid grey -cursor_name left_ptr 
vncconfig -iconic &
x-terminal-emulator -geometry 80x24+10+10 -ls -title "$VNCDESKTOP Desktop" &
x-window-manager &
gnome-session &        
gnome-panel &
gnome-settings-daemon &
metacity &
nautilus &" > .vnc/xstartup;
chmod 755 .vnc/xstartup;

wget https://github.com/bisq-network/exchange/releases/download/v0.4.9.9.3/Bitsquare-64bit-0.4.9.9.3.deb;
sudo dpkg -i Bitsquare-64bit-0.4.9.9.3.deb;

```
I call the script install.sh and execute it by 
```
$ bash install.sh
```
install.sh can be in $HOME/bin or for simplicity just under $HOME (your home directory).
Then start vncserver with the command:
```
$ vncserver
```
which will prompt you for two passwords, one for interactive vnc and one for view-only vnc. The start vncserver again using the following command which allows vnc also after a logout:
```
$ nohup vncserver -geometry 1280x1024&
```
One then has to open the firewall on the instance which is best explained in: https://goo.gl/1Ljy7O
This is done on the gce console in the web browser. It seems that google manages a firewall and it is not possible to open it from the instance.
You can now login to your VPS desktop using the vncviewer on your local computer, using the IP-adress of your instance followed by the right port (5901), exemplified by 123.45.67.89:5901. There should be a nicely looking desktop in your vncviewer, either interactive or view-only depending on which password you used. I use Jump Desk as a vncviewer, which I bought for about $35 from Apple Store. Bisq is found under under the menu Applications/Other and may be opened by clicking it.

I have tried to use vnc on my Android Nexus phone and can interact with Bisq also then (using Jump Desktop). It is thus seems possible to trade using a mobile phone.


IMPORTANT  I have found that ssh from the gce console can become nonworking quite easily. I suggest that you find another safer way to access your vps-computer using, e. g., ssh from a terminal. I have had to redo this installation about six times now due to this serious problem. 
