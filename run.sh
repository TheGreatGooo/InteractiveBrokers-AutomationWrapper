#!/bin/sh

chmod u+x Scripts/IBController.sh

tail -F /tws/**/ibgateway.log &

/usr/bin/xvfb-run -l -s ":99 -auth /tmp/xvfb.auth -ac -screen 0 1920x1080x24" Scripts/IBController.sh 1023 -g --tws-path=/tws --tws-settings-path=/tws --ibc-path=$PWD/target --ibc-ini=/ibc-config/IBController.ini
