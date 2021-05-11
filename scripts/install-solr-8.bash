#!/usr/bin/env bash

SOLR_BIN="solr8"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
CONF_DIR="${SCRIPT_DIR}/../solr-conf"
ZK_IP="localhost"
ZK_PORT="9983"
ZK_HOST="${ZK_IP}:${ZK_PORT}"

for COLLECTION in reactome target ; do
    ${SOLR_BIN} zk upconfig -d "${CONF_DIR}/${COLLECTION}" -n "${COLLECTION}" -z ${ZK_HOST}
    ${SOLR_BIN} create -c "${COLLECTION}" -n "${COLLECTION}"
done
