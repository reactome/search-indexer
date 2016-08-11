#!/usr/bin/env bash

#-----------------------------------------------------------
# Script that automates the Reactome Solr initial setup.
# Execute the files as $sudo ./setup_solr.sh -h
#
# Florian Korninger - fkorn@ebi.ac.uk
# Guilherme Viteri  - gviteri@ebi.ac.uk
#
#-----------------------------------------------------------

usage="$(basename "$0") <execution_type -a, -b or -c> -m <solr_passwd> [-d <neo4j_host> -e <neo4j_port> —f <neo4j_user> -g <neo4j_passwd> -j <solr_core> -k <sorl_port> -l <solr_user> -n <solr_version> -o <interactors_db_path> -p <smtp_server> -q <smtp_port> -r <mail_from> -s -t -u <git_branch>] -- program to auto setup the Apache Lucene Solr in Reactome environment.

where:
    -h  Program help/usage

    Execution Type:
        -a  Install Solr                DEFAULT: false
        -b  Update Solr core            DEFAULT: false
        -c  Import Reactome data        DEFAULT: false

    Arguments:
        -d  Neo4j Host                  DEFAULT: localhost
        -e  Neo4j Port                  DEFAULT: 7474
        -f  Neo4j User                  DEFAULT: neo4j
        -g  Neo4j Password              REQUIRED

        -j  Solr Core name              DEFAULT: reactome
        -k  Solr Port                   DEFAULT: 8983
        -l  Solr User                   DEFAULT: admin
        -m  Solr Password               REQUIRED
        -n  Solr Version                DEFAULT: 6.1.0

        -o  Interactors database path   DEFAULT: /usr/local/reactomes/Reactome/production/ContentService/interactors.db

        -p  Mail Smtp server            DEFAULT: smtp.oicr.on.ca
        -q  Mail Smtp port              DEFAULT: 25
        -r  Mail From                   DEFAULT: reactome-developer@reactome.org

        -s  XML output for EBeye        DEFAULT: false
        -t  Send indexing report mail   DEFAULT: false

        -u  Indexer GitHub Branch       DEFAULT: master"

# Default values
_INSTALL_SOLR=false
_UPDATE_SOLR_CORE=false
_IMPORT_DATA=false

_SOLR_HOME="/var/solr"

_SOLR_CORE="reactome"
_SOLR_PORT=8983
_SOLR_USER="admin"
_SOLR_PASSWORD=""
_SOLR_VERSION="6.1.0"

_NEO4J_HOST="localhost"
_NEO4J_PORT="7474"
_NEO4J_USER="neo4j"
_NEO4J_PASSWORD=""

_INTERACTORS_DB="/usr/local/reactomes/Reactome/production/ContentService/interactors.db"

_MAIL_SMTP="smtp.oicr.on.ca"
_MAIL_PORT="25"
_MAIL_DEST="reactome-developer@reactome.org"

_XML=""
_MAIL=""

_GIT_BRANCH="master"

while getopts ":d:e:f:g:v:j:k:l:m:n:o:p:q:r:ustabch" option; do
    case "$option" in
        h) echo "$usage"
            exit
            ;;
        a) _INSTALL_SOLR=true
            ;;
        b) _UPDATE_SOLR_CORE=true
            ;;
        c) _IMPORT_DATA=true
            ;;
        d) _NEO4J_HOST=$OPTARG
            ;;
        e) _NEO4J_PORT=$OPTARG
            ;;
        f) _NEO4J_USER=$OPTARG
            ;;
        g) _NEO4J_PASSWORD=$OPTARG
            ;;
        v) _REACTOME_PASSWORD=$OPTARG
            ;;
        j) _SOLR_CORE=$OPTARG
            ;;
        k) _SOLR_PORT=$OPTARG
            ;;
        l) _SOLR_USER=$OPTARG
            ;;
        m) _SOLR_PASSWORD=$OPTARG
            ;;
        n) _SOLR_VERSION=$OPTARG
            ;;
        o) _INTERACTORS_DB=$OPTARG
            ;;
        p) _MAIL_SMTP=$OPTARG
            ;;
        q) _MAIL_PORT=$OPTARG
            ;;
        r) _MAIL_DEST=$OPTARG
            ;;
        s) _XML="-l"
            ;;
        t) _MAIL="-m"
            ;;
        u) _GIT_BRANCH=$OPTARG
            ;;
        :) printf "missing argument for -%s\n" "$OPTARG" >&2
            echo "$usage" >&2
            exit 1
            ;;
        \?) printf "illegal option: -%s\n" "$OPTARG" >&2
            echo "$usage" >&2
            exit 1
            ;;
    esac
done
shift $((OPTIND - 1))

# --- Check if the execution type has been passed --- #
if ! $_INSTALL_SOLR && ! $_UPDATE_SOLR_CORE && ! $_IMPORT_DATA ; then
 echo "missing argument execution type -a|-b|-c"
    echo "$usage"
    exit 1
fi;

installSolr () {

    if [ -z $_SOLR_PASSWORD ]; then
        echo "missing argument for -m <password>"
        exit 1
    fi;

    # -- Reset flags
    _UPDATE_SOLR_CORE=false
    _IMPORT_DATA=false

    echo "Start SolR installation script"

    echo "Stopping current SolR installation."
    sudo service solr stop >/dev/null 2>&1

    echo "Deleting old Solr installed instances"

    # On SolR 5.5.1 default home is /var/solr
    sudo rm -rf /var/solr* >/dev/null 2>&1

    # Delete any solr previous installation
    sudo rm -rf /opt/solr*  >/dev/null 2>&1
    sudo rm -rf /etc/solr* >/dev/null 2>&1
    sudo rm -rf /usr/share/solr*  >/dev/null 2>&1
    sudo rm -rf /etc/init.d/solr
    sudo rm -rf /var/log/solr >/dev/null 2>&1
    sudo rm -rf /var/lib/solr >/dev/null 2>&1
    sudo rm -rf /var/lib/sudo/solr >/dev/null 2>&1
    sudo rm -rf /etc/default/solr.in.sh >/dev/null 2>&1

    sudo deluser --remove-home solr  >/dev/null 2>&1
    sudo deluser --group solr  >/dev/null 2>&1

    if [ -f /tmp/solr-$_SOLR_VERSION.tgz ]; then
        echo "The specified version of Solr was found in /tmp"
        if tar -tf /tmp/solr-$_SOLR_VERSION.tgz >/dev/null 2>&1 ; then
            _VALID=true
        else
            echo "The file found was corrupted"
            _VALID=false
        fi
    fi

    if ! [ $_VALID ]; then
        sudo rm /tmp/solr-$_SOLR_VERSION.tgz >/dev/null 2>&1;
        echo "Attempting to download Solr with version: "$_SOLR_VERSION

        # Download solr tgz file
        wget http://www-eu.apache.org/dist/lucene/solr/$_SOLR_VERSION/solr-$_SOLR_VERSION.tgz -P /tmp

        # Download MD5 - Used to check the integrity of solr downloaded file
        wget http://www-eu.apache.org/dist/lucene/solr/$_SOLR_VERSION/solr-$_SOLR_VERSION.tgz.md5 -P /tmp

        _MD5_SOLR=$(md5sum /tmp/solr-$_SOLR_VERSION.tgz | cut -d ' ' -f 1) >/dev/null 2>&1;
        _MD5_MD5=$(cat /tmp/solr-$_SOLR_VERSION.tgz.md5 | cut -d ' ' -f 1) >/dev/null 2>&1;

	      rm /tmp/solr-$_SOLR_VERSION.tgz.md5

        if [ $_MD5_SOLR != $_MD5_MD5 ]; then
            echo "Could not download Solr version $_SOLR_VERSION. Please check the specified version and try again"
            exit 1;
        fi
    fi

    echo "Extracting Solr installation script"
    if ! tar xzf /tmp/solr-$_SOLR_VERSION.tgz solr-$_SOLR_VERSION/bin/install_solr_service.sh --strip-components=2; then
        echo "Could not extract Solr successfully"
        exit 1;
    fi

    echo "Installing Solr"
    if ! sudo bash ./install_solr_service.sh /tmp/solr-$_SOLR_VERSION.tgz -p $_SOLR_PORT; then
        echo "Could not install Solr successfully"
        exit 1;
    fi

    rm install_solr_service.sh

    echo "Downloading latest Solr configuration from git"

    # Default directory in SolR classpath to add the config files.
    _SOLR_DATA_DIR=$_SOLR_HOME/data
    _SOLR_CORE_CONF_DIR=$_SOLR_DATA_DIR/$_SOLR_CORE/conf

    sudo mkdir -p $_SOLR_CORE_CONF_DIR

    # As we are maintaining two repositories, I will leave this option until we merge them
    read -p "[1] GitHub or [2] BitBucket (Type 2 for the new configuration): [1] " _GIT_OPTION

    if [ $_GIT_OPTION = 2 ] ; then

    	read -p "bitbucket.org email: `echo $'\n> '`" _GIT_USER
    	read -s -p "bitbucket.org password: `echo $'\n> '`" _GIT_PASSWD
	    read -p "bitbucket.org commit ID: `echo $'\n> '`" _GIT_COMMIT_ID

	    echo "Updating SolR Configuration files based on BitBucket"
    	sudo wget -q --user=$_GIT_USER --password=$_GIT_PASSWD https://bitbucket.org/fkorn/indexer/raw/$_GIT_COMMIT_ID/solr-conf/schema.xml -O $_SOLR_CORE_CONF_DIR/schema.xml >/dev/null 2>&1
	    sudo wget -q --user=$_GIT_USER --password=$_GIT_PASSWD https://bitbucket.org/fkorn/indexer/raw/$_GIT_COMMIT_ID/solr-conf/solrconfig.xml -O $_SOLR_CORE_CONF_DIR/solrconfig.xml >/dev/null 2>&1
    	sudo wget -q --user=$_GIT_USER --password=$_GIT_PASSWD https://bitbucket.org/fkorn/indexer/raw/$_GIT_COMMIT_ID/solr-conf/stopwords.txt -O $_SOLR_CORE_CONF_DIR/stopwords.txt >/dev/null 2>&1
    	sudo wget -q --user=$_GIT_USER --password=$_GIT_PASSWD https://bitbucket.org/fkorn/indexer/raw/$_GIT_COMMIT_ID/solr-conf/prefixstopwords.txt -O $_SOLR_CORE_CONF_DIR/prefixstopwords.txt >/dev/null 2>&1

    else
       echo "Updating SolR Configuration files based on GitHub"
       sudo su - solr -c "wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/schema.xml -O $_SOLR_CORE_CONF_DIR/schema.xml"
       sudo su - solr -c "wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/solrconfig.xml -O $_SOLR_CORE_CONF_DIR/solrconfig.xml"
       sudo su - solr -c "wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/stopwords.txt -O $_SOLR_CORE_CONF_DIR/stopwords.txt"
       sudo su - solr -c "wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/prefixstopwords.txt -O $_SOLR_CORE_CONF_DIR/prefixstopwords.txt"
    fi

    sudo chown -R solr:solr $_SOLR_DATA_DIR/$_SOLR_CORE

    echo "Creating new Solr core"

    _STATUS=$(curl --write-out "%{http_code}\n" --silent --output /dev/null "http://localhost:$_SOLR_PORT/solr/admin/cores?action=CREATE&name=$_SOLR_CORE")
    if [ 200 != "$_STATUS" ]; then
        echo "Could not create new Solr core "$_SOLR_CORE" status is: "$_STATUS
        exit 1;
    fi
    echo "Solr core has been created."

    echo "Enabling Solr admin authentication in Jetty"
    sudo wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-jetty-conf/jetty.xml  -O /opt/solr-$_SOLR_VERSION/server/etc/jetty.xml
    sudo wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-jetty-conf/webdefault.xml  -O /opt/solr-$_SOLR_VERSION/server/etc/webdefault.xml

    sudo bash -c "echo $_SOLR_USER: '$_SOLR_PASSWORD',solr-admin > /opt/solr-$_SOLR_VERSION/server/etc/realm.properties"

    echo "Restart solr service..."
    if ! sudo service solr restart; then
        echo "Could not restart Solr server"
    fi

    echo "Successfully installed Solr."

}

updateSolrConfigFiles () {

    # --- Check mandatories arguments for UPDATE_SOLR_CORE --- #
    if [ -z $_SOLR_PASSWORD ]; then
        echo "missing argument for -m <solr_passwd>"
        exit 1
    fi;

    # -- Reset flags
    _INSTALL_SOLR=false
    _IMPORT_DATA=false

    echo "Checking if Solr is running"
    _STATUS=$(curl -H "Content-Type: application/json" --user $_SOLR_USER:$_SOLR_PASSWORD --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:$_SOLR_PORT/solr/admin/cores?action=STATUS)
    if [ 200 != "$_STATUS" ]; then
        if ! sudo service solr start >/dev/null 2>&1; then
            echo "Solr is not running and can not be started"
            exit 1;
        fi
    fi

    echo "Checking if Reactome core is available"
    _STATUS=$(curl -H "Content-Type: application/json" --user $_SOLR_USER:$_SOLR_PASSWORD --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:$_SOLR_PORT/solr/$_SOLR_CORE/admin/ping)
    if [ 200 != "$_STATUS" ]; then
        echo "Reactome core is not available. Installation required."
        exit 1;
    fi

    echo "Shutting down Solr for updating the core"
    sudo service solr stop >/dev/null 2>&1

    if sudo [ !  -d "$_SOLR_HOME/data/$_SOLR_CORE/conf" ]; then
        echo "Wrong Solr home path was specified please check again"
        exit 1;
    fi

    echo "Downloading latest Solr configuration from git"
    _SOLR_CORE_CONF_DIR=$_SOLR_HOME/data/$_SOLR_CORE/conf

    # As we are maintaining two repositories, I will leave this option until we merge them
    read -p "[1] GitHub or [2] BitBucket (Type 2 for the new configuration): [1] " _GIT_OPTION

    if [ $_GIT_OPTION = 2 ] ; then

    	read -p "bitbucket.org email: `echo $'\n> '`" _GIT_USER
    	read -s -p "bitbucket.org password: `echo $'\n> '`" _GIT_PASSWD
      read -p "bitbucket.org commit ID: `echo $'\n> '`" _GIT_COMMIT_ID

      echo "Updating SolR Configuration files based on BitBucket"
    	sudo su - solr -c "wget -q --user=$_GIT_USER --password=$_GIT_PASSWD https://bitbucket.org/fkorn/indexer/raw/$_GIT_COMMIT_ID/solr-conf/schema.xml -O $_SOLR_CORE_CONF_DIR/schema.xml" >/dev/null 2>&1
	    sudo su - solr -c "wget -q --user=$_GIT_USER --password=$_GIT_PASSWD https://bitbucket.org/fkorn/indexer/raw/$_GIT_COMMIT_ID/solr-conf/solrconfig.xml -O $_SOLR_CORE_CONF_DIR/solrconfig.xml" >/dev/null 2>&1
    	sudo su - solr -c "wget -q --user=$_GIT_USER --password=$_GIT_PASSWD https://bitbucket.org/fkorn/indexer/raw/$_GIT_COMMIT_ID/solr-conf/stopwords.txt -O $_SOLR_CORE_CONF_DIR/stopwords.txt" >/dev/null 2>&1
    	sudo su - solr -c "wget -q --user=$_GIT_USER --password=$_GIT_PASSWD https://bitbucket.org/fkorn/indexer/raw/$_GIT_COMMIT_ID/solr-conf/prefixstopwords.txt -O $_SOLR_CORE_CONF_DIR/prefixstopwords.txt" >/dev/null 2>&1

    else
       echo "Updating SolR Configuration files based on GitHub"
       sudo su - solr -c "wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/schema.xml -O $_SOLR_CORE_CONF_DIR/schema.xml"
       sudo su - solr -c "wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/solrconfig.xml -O $_SOLR_CORE_CONF_DIR/solrconfig.xml"
       sudo su - solr -c "wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/stopwords.txt -O $_SOLR_CORE_CONF_DIR/stopwords.txt"
       sudo su - solr -c "wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/prefixstopwords.txt -O $_SOLR_CORE_CONF_DIR/prefixstopwords.txt"

    fi

    echo "Starting Solr"
    if ! sudo service solr start ; then
        echo "Could not start Solr server"
        exit 1;
    fi

    echo "Successfully updated Solr"

}

# SolR Data is created in $_SOLR_HOME/data/$_SOLR_CORE/data
runIndexer () {

    if [ -z $_SOLR_PASSWORD ]; then
        echo "missing argument for -m <solr_passwd>"
        exit 1
    fi;

    if [ -z $_NEO4J_PASSWORD ]; then
        echo "missing argument for -g <neo4j_passwd>"
        exit 1
    fi;

    # -- Reset flags
    _INSTALL_SOLR=false
    _UPDATE_SOLR_CORE=false

    echo "Checking if Solr is running..."
    _STATUS=$(curl -H "Content-Type: application/json" --user $_SOLR_USER:$_SOLR_PASSWORD --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:$_SOLR_PORT/solr/admin/cores?action=STATUS)
    if [ 200 != "$_STATUS" ]; then
        if ! sudo service solr start >/dev/null 2>&1; then
            echo "Solr is not running and can not be started"
            exit 1;
        fi
    fi
    echo "Solr is running!"

    echo "Checking if Reactome core is available..."
    _STATUS=$(curl -H "Content-Type: application/json" --user $_SOLR_USER:$_SOLR_PASSWORD --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:$_SOLR_PORT/solr/$_SOLR_CORE/admin/ping)
    if [ 200 != "$_STATUS" ]; then
        echo "Reactome core is not available"
        exit 1;
    fi
    echo "Reactome core is available!"

    echo "Checking if current directory is valid project"
    if ! mvn -q clean package -DskipTests ; then
        if [ ! -f /target/Indexer-jar-with-dependencies.jar ]; then

            echo "Cloning project from repository..."

	         # As we are maintaining two repositories, I will leave this option until we merge them
            read -p "[1] GitHub or [2] BitBucket (Type 2 for the new configuration): [1] " _GIT_OPTION

            if [ $_GIT_OPTION = 2 ] ; then
            	read -p "bitbucket.org username [not your email]: `echo $'\n> '`" _GIT_USER
            	git clone https://$_GIT_USER@bitbucket.org/fkorn/indexer.git
            else
                git clone https://github.com/reactome/Search.git
            fi

            git -C ./indexer/ fetch && git -C ./indexer/ checkout $_GIT_BRANCH
            _PATH="/indexer"

            echo "Started packaging reactome project"
            if ! mvn -q -f .${_PATH}/pom.xml clean package -DskipTests >/dev/null 2>&1; then
               echo "An error occurred when packaging the project."
               exit 1
	          fi
        fi
    fi

    _SOLR_URL=http://localhost:${_SOLR_PORT}/solr/${_SOLR_CORE}

     if ! java -jar .${_PATH}/target/Indexer-jar-with-dependencies.jar -a ${_NEO4J_HOST} -b ${_NEO4J_PORT} -c ${_NEO4J_USER} -d ${_NEO4J_PASSWORD} -e ${_SOLR_URL} -f ${_SOLR_USER} -g ${_SOLR_PASSWORD} -h ${_INTERACTORS_DB} -i ${_MAIL_SMTP} -j ${_MAIL_PORT} -k ${_MAIL_DEST} ${_XML} ${_MAIL}; then
        echo "An error occurred during the Solr-Indexer process. Please check logs."
        exit 1
     fi

    echo "Successfully imported data to Solr!"
}

summary () {
   echo "============================"
   echo "=========== SOLR ==========="
   echo "Install SolR:       " $_INSTALL_SOLR
   echo "Update SolR:        " $_UPDATE_SOLR_CORE
   echo "Run Indexer:        " $_IMPORT_DATA
   echo "neo4j host:         " "http://"$_NEO4J_HOST":"$_NEO4J_PORT
   echo "neo4j user:         " $_NEO4J_USER
   echo "SolR Default Home:  " $_SOLR_HOME
   echo "SolR Core:          " $_SOLR_CORE
   echo "SolR Port:          " $_SOLR_PORT
   echo "SolR User:          " $_SOLR_USER
   echo "SolR Version:       " $_SOLR_VERSION
   echo "Interactors DB:     " $_INTERACTORS_DB
   echo "SMTP Server:        " $_MAIL_SMTP":"$_MAIL_PORT
   echo "Mail Destination:   " $_MAIL_DEST
   echo "GitHub Branch:      " $_GIT_BRANCH
   echo "============================"
}

# -- Print variables
summary

# --- Install SOLR, Create reactome core and set security --- #
if ${_INSTALL_SOLR} = true; then
    installSolr
fi

# --- Update SOLR Configuration files --- #
if ${_UPDATE_SOLR_CORE} = true; then
    updateSolrConfigFiles
fi

# --- RUN INDEXER --- #
if ${_IMPORT_DATA} = true; then
    runIndexer
fi

echo "DONE. Bye!"
