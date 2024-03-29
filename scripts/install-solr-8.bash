#!/usr/bin/env bash

#-----------------------------------------------------------
# Script that automates the Reactome Solr initial setup.
# Execute the files as $sudo ./install-solr.sh -h
#
# Florian Korninger - fkorn@ebi.ac.uk
# Guilherme Viteri  - gviteri@ebi.ac.uk
#
#-----------------------------------------------------------

# Default value
_SOLR_HOME="/var/solr"

_SOLR_CORE="reactome"
_SOLR_USER="admin"
_SOLR_PASSWORD=""
_SOLR_PORT=8983
_SOLR_VERSION="8.9.0"

_GITREPO="reactome"
_GITPROJECT="search-indexer"
_GITRAWURL="https://raw.githubusercontent.com"
_GITBRANCH="master"

if [[ $(id -u) -ne 0 ]]; then
  echo "Please run as sudo."
  exit 1
fi

usage() {
  echo "Program to auto setup the Apache Lucene Solr in Reactome environment."
  echo "usage: sudo ./$(basename "$0") solrpass=<solr_passwd> "
  echo "              OPTIONAL solrcore=<solr_core>"
  echo "                       solruser=<solr_user>"
  echo "                       solrport=<solr_port>"
  echo "                       solrversion=<solr_version>"
  echo "                       gitbranch=<git_branch>"
  echo ""
  echo "   where:"
  echo "       solrpass         REQUIRED"
  echo "       solrcore         DEFAULT: reactome"
  echo "       solruser         DEFAULT: admin"
  echo "       solrport         DEFAULT: 8983"
  echo "       solrversion      DEFAULT: 8.9.0"
  echo "       gitbranch        DEFAULT: master (Download Solr configuration from git)"
  echo ""
  echo "e.g sudo ./$(basename "$0") solrpass=not2share"
  echo "e.g sudo ./$(basename "$0") solrpass=not2share solrcore=pathways gitbranch=dev"

  exit
}

# Check arguments
for ARGUMENT in "$@"; do
  KEY=$(echo "${ARGUMENT}" | cut -f1 -d=)
  VALUE=$(echo "${ARGUMENT}" | cut -f2 -d=)

  case "$KEY" in
  solrcore) _SOLR_CORE=${VALUE} ;;
  solruser) _SOLR_USER=${VALUE} ;;
  solrpass) _SOLR_PASSWORD=${VALUE} ;;
  solrport) _SOLR_PORT=${VALUE} ;;
  solrversion) _SOLR_VERSION=${VALUE} ;;
  gitbranch) _GITBRANCH=${VALUE} ;;
  help) _HELP="help-me" ;;
  -h) _HELP="help-me" ;;
  *) ;;
  esac
done

if [[ "${_HELP}" == "help-me" ]]; then
  usage
fi

if [[ -z ${_SOLR_PASSWORD} ]]; then
  echo "missing argument for solrpass=<password>"
  exit 1
fi

verify_branch() {
  echo " > Validating GitHub Branch"
  STATUS=$(curl -X GET -w "%{http_code}" --silent --output /dev/null "https://github.com/$_GITREPO/$_GITPROJECT/tree/$_GITBRANCH/")
  if [[ 200 != "${STATUS}" ]]; then
    echo " > Invalid GitHub Branch: $_GITBRANCH"
    exit 1
  fi
  echo " > Branch OK."
}

installSolr() {
  echo ""
  echo "Start SolR installation script"

  verify_branch

  echo "Stopping current SolR installation."
  sudo service solr stop >/dev/null 2>&1

  echo "Deleting old Solr installed instances"

  # On SolR 5.5.1 default home is /var/solr
  sudo rm -rf /var/solr* >/dev/null 2>&1

  # Delete any solr previous installation
  sudo rm -rf /opt/solr* >/dev/null 2>&1
  sudo rm -rf /etc/solr* >/dev/null 2>&1
  sudo rm -rf /usr/share/solr* >/dev/null 2>&1
  sudo rm -rf /etc/init.d/solr
  sudo rm -rf /var/log/solr >/dev/null 2>&1
  sudo rm -rf /var/lib/solr >/dev/null 2>&1
  sudo rm -rf /var/lib/sudo/solr >/dev/null 2>&1
  sudo rm -rf /etc/default/solr.in.sh >/dev/null 2>&1

  sudo deluser --remove-home solr >/dev/null 2>&1
  sudo deluser --group solr >/dev/null 2>&1

  if [[ -f /tmp/solr-${_SOLR_VERSION}.tgz ]]; then
    echo "The specified version of Solr was found in /tmp"
    if tar -tf "/tmp/solr-${_SOLR_VERSION}.tgz" >/dev/null 2>&1; then
      _VALID=true
    else
      echo "The file found was corrupted"
      _VALID=false
    fi
  fi

  if ! [[ ${_VALID} ]]; then
    sudo rm "/tmp/solr-${_SOLR_VERSION}.tgz" >/dev/null 2>&1
    echo "Attempting to download Solr with version: ${_SOLR_VERSION}"

    # Download solr tgz file
    wget -q --show-progress "http://archive.apache.org/dist/lucene/solr/${_SOLR_VERSION}/solr-${_SOLR_VERSION}.tgz" -P /tmp

  fi

  echo "Extracting Solr installation script"
  if ! tar xzf "/tmp/solr-${_SOLR_VERSION}.tgz" "solr-${_SOLR_VERSION}/bin/install_solr_service.sh" --strip-components=2; then
    echo "Could not extract Solr successfully"
    exit 1
  fi

  echo "Installing Solr"
  sudo bash ./install_solr_service.sh "/tmp/solr-${_SOLR_VERSION}.tgz" -p "${_SOLR_PORT}" >/dev/null 2>&1
  OUT=$?
  if [[ "$OUT" -ne 0 ]]; then
    echo "Could not install Solr successfully. Check the error and run the script again."
    exit 1
  fi

  rm install_solr_service.sh

  echo "Creating Reactome Solr core"
  # Default directory in SolR classpath to add the config files.
  _SOLR_DATA_DIR=${_SOLR_HOME}/data
  _SOLR_CORE_CONF_DIR=${_SOLR_DATA_DIR}/${_SOLR_CORE}/conf

  sudo mkdir -p "${_SOLR_CORE_CONF_DIR}"

  echo "Updating SolR Configuration files based on GitHub"
  sudo wget -q --no-check-certificate "${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_GITBRANCH}/solr-conf/${_SOLR_CORE}/schema.xml" -O "${_SOLR_CORE_CONF_DIR}/schema.xml" >/dev/null 2>&1
  sudo wget -q --no-check-certificate "${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_GITBRANCH}/solr-conf/${_SOLR_CORE}/solrconfig.xml" -O "${_SOLR_CORE_CONF_DIR}/solrconfig.xml" >/dev/null 2>&1
  sudo wget -q --no-check-certificate "${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_GITBRANCH}/solr-conf/${_SOLR_CORE}/stopwords.txt" -O "${_SOLR_CORE_CONF_DIR}/stopwords.txt" >/dev/null 2>&1
  sudo wget -q --no-check-certificate "${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_GITBRANCH}/solr-conf/${_SOLR_CORE}/prefixstopwords.txt" -O "${_SOLR_CORE_CONF_DIR}/prefixstopwords.txt" >/dev/null 2>&1

  sudo chown -R solr:solr "${_SOLR_DATA_DIR}/${_SOLR_CORE}"

  _STATUS=$(curl --write-out "%{http_code}\n" --silent --output /dev/null "http://localhost:$_SOLR_PORT/solr/admin/cores?action=CREATE&name=$_SOLR_CORE")
  if [[ 200 != "$_STATUS" ]]; then
    echo "Could not create new Solr core ${_SOLR_CORE} status is: ${_STATUS}"
    exit 1
  fi
  echo "Reactome core has been created."

  echo "Creating Target Solr core"
  # Default directory in SolR classpath to add the config files.
  _TARGET_CORE=target
  _TARGET_DATA_DIR=${_SOLR_HOME}/data
  _TARGET_CORE_CONF_DIR=${_TARGET_DATA_DIR}/${_TARGET_CORE}/conf

  sudo mkdir -p ${_TARGET_CORE_CONF_DIR}

  sudo wget -q --no-check-certificate "${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_GITBRANCH}/solr-conf/${_TARGET_CORE}/schema.xml" -O "${_TARGET_CORE_CONF_DIR}/schema.xml" >/dev/null 2>&1
  sudo wget -q --no-check-certificate "${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_GITBRANCH}/solr-conf/${_TARGET_CORE}/solrconfig.xml" -O "${_TARGET_CORE_CONF_DIR}/solrconfig.xml" >/dev/null 2>&1
  sudo wget -q --no-check-certificate "${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_GITBRANCH}/solr-conf/${_TARGET_CORE}/stopwords.txt" -O "${_TARGET_CORE_CONF_DIR}/stopwords.txt" >/dev/null 2>&1
  sudo wget -q --no-check-certificate "${_GITRAWURL}/${_GITREPO}/${_GITPROJECT}/${_GITBRANCH}/solr-conf/${_TARGET_CORE}/prefixstopwords.txt" -O "${_TARGET_CORE_CONF_DIR}/prefixstopwords.txt" >/dev/null 2>&1

  sudo chown -R solr:solr ${_TARGET_DATA_DIR}/${_TARGET_CORE}

  _STATUS=$(curl --write-out "%{http_code}\n" --silent --output /dev/null "http://localhost:$_SOLR_PORT/solr/admin/cores?action=CREATE&name=$_TARGET_CORE")
  if [[ 200 != "$_STATUS" ]]; then
    echo "Could not create new Solr core [$_TARGET_CORE] status is: ${_STATUS}"
    exit 1
  fi
  echo "Target core has been created."

  echo "Enabling Solr admin authentication"
  _MVN=$(command -v mvn)
  _INDEXER_PROJECT="/home/${SUDO_USER}/${_GITPROJECT}"
  echo "Packaging the project in preparation for real-time password encryption"
  if ! ${_MVN} -q -U clean package -f "${_INDEXER_PROJECT}/pom.xml" -DskipTests >/dev/null 2>&1 ; then
      if [[ ! -f ./target/search-indexer-exec.jar ]]; then
          echo "An error occurred when packaging the project."
          exit 1
      fi
  fi
  sudo java -cp "${_INDEXER_PROJECT}/target/search-indexer-exec.jar" -Dloader.main=org.reactome.server.tools.indexer.util.SHA256SolrPassword org.springframework.boot.loader.PropertiesLauncher "${_SOLR_PASSWORD}" "${_SOLR_DATA_DIR}"
  sudo "${_MVN}" -q clean -f "${_INDEXER_PROJECT}/pom.xml" >/dev/null 2>&1

  echo "Setting solr owner to security.json"
  sudo chown solr:solr ${_SOLR_DATA_DIR}/security.json

  echo "Restart solr service..."
  if ! sudo service solr restart; then
    echo "Installation finished but could not restart Solr server properly"
    exit 1
  fi

  echo "Successfully installed Solr."
}

generalSummary() {
  echo "======================================"
  echo "=========== INSTALL SOLR ============="
  echo "======================================"
  echo "SolR Default Home:   ${_SOLR_HOME} "
  echo "SolR Core:           ${_SOLR_CORE} "
  echo "SolR Port:           ${_SOLR_PORT} "
  echo "SolR User:           ${_SOLR_USER} "
  echo "SolR Version:        ${_SOLR_VERSION} "
  echo "Git Branch:          ${_GITBRANCH} "
  echo "======================================"
}

# -- Print variables
generalSummary

installSolr

echo "DONE. Bye!"
