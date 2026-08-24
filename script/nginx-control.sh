#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
set -eo pipefail

cmd=${1:-start}
BIN_DIR=$(cd "$(dirname "$0")"; pwd)
DOLPHINSCHEDULER_HOME=$(cd "$BIN_DIR/.."; pwd)

DOLPHINSCHEDULER_NGINX_ENABLED=${DOLPHINSCHEDULER_NGINX_ENABLED:-true}
DOLPHINSCHEDULER_NGINX_PORT=${DOLPHINSCHEDULER_NGINX_PORT:-8888}
DOLPHINSCHEDULER_PUBLIC_API=${DOLPHINSCHEDULER_PUBLIC_API:-127.0.0.1:12345}
DOLPHINSCHEDULER_UI_ROOT=${DOLPHINSCHEDULER_UI_ROOT:-$DOLPHINSCHEDULER_HOME/ui}
DOLPHINSCHEDULER_TENANT_CONF_DIR=${DOLPHINSCHEDULER_TENANT_CONF_DIR:-$DOLPHINSCHEDULER_HOME/conf/nginx/tenants}
DOLPHINSCHEDULER_NGINX_CONF_TEMPLATE=${DOLPHINSCHEDULER_NGINX_CONF_TEMPLATE:-$DOLPHINSCHEDULER_HOME/conf/nginx/dolphinscheduler.conf.template}
DOLPHINSCHEDULER_NGINX_CONF=${DOLPHINSCHEDULER_NGINX_CONF:-$DOLPHINSCHEDULER_HOME/conf/nginx/dolphinscheduler.conf}
DOLPHINSCHEDULER_NGINX_PID=${DOLPHINSCHEDULER_NGINX_PID:-$DOLPHINSCHEDULER_HOME/logs/nginx.pid}
DOLPHINSCHEDULER_NGINX_ERROR_LOG=${DOLPHINSCHEDULER_NGINX_ERROR_LOG:-$DOLPHINSCHEDULER_HOME/logs/nginx-error.log}
DOLPHINSCHEDULER_NGINX_ACCESS_LOG=${DOLPHINSCHEDULER_NGINX_ACCESS_LOG:-$DOLPHINSCHEDULER_HOME/logs/nginx-access.log}

function skip_if_disabled() {
  if [ "$DOLPHINSCHEDULER_NGINX_ENABLED" != "true" ]; then
    echo "Skip nginx proxy because DOLPHINSCHEDULER_NGINX_ENABLED is not true."
    exit 0
  fi
  if ! command -v nginx >/dev/null 2>&1; then
    echo "Skip nginx proxy because nginx command was not found."
    exit 0
  fi
}

function escape_sed_replacement() {
  printf '%s' "$1" | sed 's/[\\&|]/\\&/g'
}

function render_config() {
  if [ ! -f "$DOLPHINSCHEDULER_NGINX_CONF_TEMPLATE" ]; then
    echo "Skip nginx proxy because template does not exist: $DOLPHINSCHEDULER_NGINX_CONF_TEMPLATE"
    exit 0
  fi
  mkdir -p "$(dirname "$DOLPHINSCHEDULER_NGINX_CONF")" \
           "$DOLPHINSCHEDULER_TENANT_CONF_DIR" \
           "$(dirname "$DOLPHINSCHEDULER_NGINX_PID")" \
           "$(dirname "$DOLPHINSCHEDULER_NGINX_ERROR_LOG")" \
           "$(dirname "$DOLPHINSCHEDULER_NGINX_ACCESS_LOG")"
  touch "$DOLPHINSCHEDULER_TENANT_CONF_DIR/_empty.conf"

  sed \
    -e "s|@DOLPHINSCHEDULER_NGINX_PID@|$(escape_sed_replacement "$DOLPHINSCHEDULER_NGINX_PID")|g" \
    -e "s|@DOLPHINSCHEDULER_NGINX_ERROR_LOG@|$(escape_sed_replacement "$DOLPHINSCHEDULER_NGINX_ERROR_LOG")|g" \
    -e "s|@DOLPHINSCHEDULER_NGINX_ACCESS_LOG@|$(escape_sed_replacement "$DOLPHINSCHEDULER_NGINX_ACCESS_LOG")|g" \
    -e "s|@DOLPHINSCHEDULER_NGINX_PORT@|$(escape_sed_replacement "$DOLPHINSCHEDULER_NGINX_PORT")|g" \
    -e "s|@DOLPHINSCHEDULER_PUBLIC_API@|$(escape_sed_replacement "$DOLPHINSCHEDULER_PUBLIC_API")|g" \
    -e "s|@DOLPHINSCHEDULER_UI_ROOT@|$(escape_sed_replacement "$DOLPHINSCHEDULER_UI_ROOT")|g" \
    -e "s|@DOLPHINSCHEDULER_TENANT_CONF_DIR@|$(escape_sed_replacement "$DOLPHINSCHEDULER_TENANT_CONF_DIR")|g" \
    "$DOLPHINSCHEDULER_NGINX_CONF_TEMPLATE" > "$DOLPHINSCHEDULER_NGINX_CONF"
}

function nginx_config_ok() {
  nginx -t -c "$DOLPHINSCHEDULER_NGINX_CONF"
}

function nginx_is_running() {
  [ -f "$DOLPHINSCHEDULER_NGINX_PID" ] && kill -0 "$(cat "$DOLPHINSCHEDULER_NGINX_PID")" >/dev/null 2>&1
}

function start_or_reload() {
  render_config
  if ! nginx_config_ok; then
    echo "Skip nginx proxy because generated config is invalid."
    exit 0
  fi
  if nginx_is_running; then
    nginx -c "$DOLPHINSCHEDULER_NGINX_CONF" -s reload
    echo "Nginx proxy reloaded with $DOLPHINSCHEDULER_NGINX_CONF."
  else
    nginx -c "$DOLPHINSCHEDULER_NGINX_CONF"
    echo "Nginx proxy started with $DOLPHINSCHEDULER_NGINX_CONF."
  fi
}

skip_if_disabled

case "$cmd" in
  start|reload)
    start_or_reload
    ;;
  stop)
    if nginx_is_running; then
      nginx -c "$DOLPHINSCHEDULER_NGINX_CONF" -s stop
      echo "Nginx proxy stopped."
    else
      echo "Nginx proxy is not running."
    fi
    ;;
  status)
    if nginx_is_running; then
      echo "Nginx proxy is running."
    else
      echo "Nginx proxy is stopped."
    fi
    ;;
  *)
    echo "Usage: nginx-control.sh (start|reload|stop|status)"
    exit 1
    ;;
esac
