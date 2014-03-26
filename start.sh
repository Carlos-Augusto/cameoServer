#!/bin/bash

# stop the app fist ;)
./stop.sh

# get app mode
if [ -e "mode" ]; then
    mode=$(cat mode)
else
    echo -e "\e[33m[cameo - mode file not found. exiting]\033[0m"
    exit 1
fi

case "$mode" in
   "prod")
      echo -e "\e[33m[cameo - Starting app as prod]\033[0m"
      app_options=-Dconfig.file=/opt/cameoSecrets/secret_prod.conf
      ;;
   "stage")
      echo -e "\e[33m[cameo - Starting app as stage]\033[0m"
      app_options=-Dconfig.file=/opt/cameoSecrets/secret_stage.conf
      ;;
   "dev")
       echo -e "\e[33m[cameo - Starting app as dev]\033[0m"
      app_options=-Dconfig.file=/opt/cameoSecrets/secret_dev.conf
      ;;
   "local")
      echo -e "\e[33m[cameo - Starting app as local]\033[0m"
      app_options=-Dconfig.file=/opt/cameoSecrets/secret_local.conf
      ;;
   *)
      echo "\e[33m[cameo - Invalid mode: ${mode}]\033[0m"
      exit 1
      ;;
esac

nohup bash -c "./target/universal/stage/bin/cameoserver $app_options > /dev/null" &> /dev/null &
