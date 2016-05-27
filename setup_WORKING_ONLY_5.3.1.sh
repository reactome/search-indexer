#!/usr/bin/env bash


#-----------------------------------------------------------
# Script that automates the Reactome Solr initial setup.
# Execute the files as $sudo ./setup_solr.sh -h
#
#
# 19 October 2015
# Florian Korninger - fkorn@ebi.ac.uk
# Guilherme Viteri  - gviteri@ebi.ac.uk
#
#-----------------------------------------------------------

usage="$(basename "$0") <execution_type -a, -b or -c> -m <solr_passwd> [-d <db_host> -e <db_port> —f <db_name> -g <db_user> -v <db_passwd> -i <solr_home_dir> -j <solr_core> -k <sorl_port> -l <solr_user> -n <solr_version> -o <interactors_db_path> -p <smtp_server> -q <smtp_port> -r <mail_from> -s -t -u <git_branch>] -- program to auto setup the Apache Lucene Solr in Reactome environment.

where:
    -h  Program help/usage

    Execution Type:
    -a  Install Solr                DEFAULT: false
    -b  Update Solr core            DEFAULT: false
    -c  Import Reactome data        DEFAULT: false

    Arguments:
    -d  Reactome database host      DEFAULT: localhost
    -e  Reactome database port      DEFAULT: 3306
    -f  Reactome database name      DEFAULT: reactome
    -g  Reactome database user      DEFAULT: reactome
    -v  Reactome database password  DEFAULT:

    -i  Solr Home directory.        DEFAULT: /usr/local/reactomes/Reactome/production/Solr

    -j  Solr Core name              DEFAULT: reactome
    -k  Solr Port                   DEFAULT: 8983
    -l  Solr User                   DEFAULT: admin
    -m  Solr Password
    -n  Solr Version                DEFAULT: 5.3.1

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

_REACTOME_HOST="localhost"
_REACTOME_PORT=3306
_REACTOME_NAME="reactome"
_REACTOME_USER="reactome"
_REACTOME_PASSWORD=""

_SOLR_HOME="/usr/local/reactomes/Reactome/production/solr"

_SOLR_CORE="reactome"
_SOLR_PORT=8983
_SOLR_USER="admin"
_SOLR_PASSWORD=""
_SOLR_VERSION="5.3.1"

_INTERACTORS_DB="/usr/local/reactomes/Reactome/production/ContentService/interactors.db"

_MAIL_SMTP="smtp.oicr.on.ca"
_MAIL_PORT="25"
_MAIL_DEST="reactome-developer@reactome.org"

_XML=""
_MAIL=""

_GIT_BRANCH="master"

while getopts ":d:e:f:g:v:i:j:k:l:m:n:o:p:q:r:ustabch" option; do
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
        d) _REACTOME_HOST=$OPTARG
            ;;
        e) _REACTOME_PORT=$OPTARG
            ;;
        f) _REACTOME_NAME=$OPTARG
            ;;
        g) _REACTOME_USER=$OPTARG
            ;;
        v) _REACTOME_PASSWORD=$OPTARG
            ;;
        i) _SOLR_HOME=$OPTARG
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
        s) _XML="-x"
            ;;
        t) _MAIL="-y"
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

# --- Install SOLR, Create reactome core and set security --- #
if ${_INSTALL_SOLR} = true; then
    if [ -z $_SOLR_PASSWORD ]; then
        echo "missing argument for -m <password>"
        echo "$usage"
        exit 1
    fi;

    echo "Start SolR installation script"

    echo "Stopping current SolR installation."
    sudo service solr stop >/dev/null 2>&1

    echo "Deleting old Solr installed instances"

    # Delete any solr previous installation
    sudo rm -rf /var/solr* >/dev/null 2>&1
    sudo rm -rf /opt/solr*  >/dev/null 2>&1
    sudo rm -rf /etc/solr* >/dev/null 2>&1
    sudo rm -rf /usr/share/solr*  >/dev/null 2>&1
    sudo rm -rf /etc/init.d/solr >/dev/null 2>&1

    # Delete SOLR_HOME. Check if the home is not single / and must end with solr
    if [ "/" == "${_SOLR_HOME: -1}" ]; then
        _SOLR_HOME=${_SOLR_HOME::-1}
    fi

    if [ "/solr" != "${_SOLR_HOME: -5}" ]; then
        _SOLR_HOME=$_SOLR_HOME"/solr"
    fi

    sudo rm -r $_SOLR_HOME >/dev/null 2>&1

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
    if ! sudo bash ./install_solr_service.sh /tmp/solr-$_SOLR_VERSION.tgz -d $_SOLR_HOME -p $_SOLR_PORT >/dev/null 2>&1; then
        echo "Could not install Solr successfully"
        exit 1;
    fi

    rm install_solr_service.sh

    echo "Downloading latest Solr configuration from git"
    sudo rm -rf /tmp/solr-conf >/dev/null 2>&1
    mkdir /tmp/solr-conf

    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/schema.xml -O /tmp/solr-conf/schema.xml
    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/solrconfig.xml -O /tmp/solr-conf/solrconfig.xml
    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/stopwords.txt -O /tmp/solr-conf/stopwords.txt
    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/prefixstopwords.txt -O /tmp/solr-conf/prefixstopwords.txt

    sudo chown -R solr:solr /tmp/solr-conf

    echo "Removing old Solr core if existing"
    sudo su - solr -c "/opt/solr/bin/solr delete -c $_SOLR_CORE" >/dev/null 2>&1

    echo "Creating new Solr core"
    if ! sudo su - solr -c "/opt/solr/bin/solr create_core -c $_SOLR_CORE -n /tmp/solr-conf" < /dev/null 2>&1; then
        echo "Could not create new Solr core"
        exit 1;
    fi

    echo "Deleting temporary solr-conf"
    #sudo rm -r /tmp/solr-conf >/dev/null 2>&1

    echo "Enabling Solr admin authentication in Jetty"
    sudo wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-jetty-conf/jetty.xml  -O /opt/solr-$_SOLR_VERSION/server/etc/jetty.xml
    sudo wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-jetty-conf/webdefault.xml  -O /opt/solr-$_SOLR_VERSION/server/etc/webdefault.xml

    sudo bash -c "echo $_SOLR_USER: '$_SOLR_PASSWORD',solr-admin > /opt/solr-$_SOLR_VERSION/server/etc/realm.properties"

    echo "Restart solr service..."
    if ! sudo service solr restart; then
        echo "Could not restart Solr server"
    fi

    echo "Successfully installed Solr."
fi

# --- Update SOLR Configuration files --- #
if ${_UPDATE_SOLR_CORE} = true; then

    # --- Check mandatories arguments for UPDATE_SOLR_CORE --- #
    if [ -z $_SOLR_PASSWORD ]; then
        echo "missing argument for -m <solr_passwd>"
        echo "$usage"
        exit 1
    fi;

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

    echo "Downloading latest Solr configuration from git"

    rm -r /tmp/solr-conf >/dev/null 2>&1
    mkdir /tmp/solr-conf

    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/schema.xml -O /tmp/solr-conf/schema.xml
    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/solrconfig.xml -O /tmp/solr-conf/solrconfig.xml
    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/stopwords.txt -O /tmp/solr-conf/stopwords.txt
    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/prefixstopwords.txt -O /tmp/solr-conf/prefixstopwords.txt

    if [ "/" == "${_SOLR_HOME: -1}" ]; then
        _SOLR_HOME=${_SOLR_HOME::-1}
    fi

    if [ "/solr" != "${_SOLR_HOME: -5}" ]; then
        echo "Wrong Solr home path was specified please check again"
        exit 1;
    fi

    if sudo [ !  -d "$_SOLR_HOME/data/$_SOLR_CORE/conf" ]; then
        echo "Wrong Solr home path was specified please check again"
        exit 1;
    fi

    sudo bash -c " cp -fr /tmp/solr-conf/* $_SOLR_HOME/data/$_SOLR_CORE/conf"

    sudo rm -r /tmp/solr-conf >/dev/null 2>&1

    echo "Starting Solr"
    if ! sudo service solr start ; then
        echo "Could not start Solr server"
        exit 1;
    fi

    echo "Successfully installed Solr"
fi

# --- RUN INDEXER --- #
if ${_IMPORT_DATA} = true; then

    if [ -z $_SOLR_PASSWORD ]; then
        echo "missing argument for -m <solr_passwd>"
        echo "$usage"
        exit 1
    fi;

    if [ -z $_REACTOME_PASSWORD ]; then
        echo "missing argument for -v <db_passwd>"
        echo "$usage"
        exit 1
    fi;

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
    if ! mvn -q clean package -DskipTests >/dev/null 2>&1; then
        if [ ! -f /target/Indexer-jar-with-dependencies.jar ]; then
            
            echo "Cloning new repo from GitHub - https://fkorn@bitbucket.org/fkorn/indexer.git"
            echo "THIS REPO HAS TO BE CHANGED ---- https://fkorn@bitbucket.org/fkorn/indexer.git"

            git clone https://fkorn@bitbucket.org/fkorn/indexer.git
            git -C ./indexer/ fetch && git -C ./indexer/  checkout $_GIT_BRANCH
            _PATH="/indexer"
            
            echo "Started packaging reactome project"
            if ! mvn -q -f .${_PATH}/pom.xml clean package -DskipTests >/dev/null 2>&1; then
                echo "An error occurred when packaging the project."
                exit 1
            fi
        fi
    fi

    _SOLR_URL=http://localhost:${_SOLR_PORT}/solr/${_SOLR_CORE}

     if ! java -jar .${_PATH}/target/Indexer-jar-with-dependencies.jar -h ${_REACTOME_HOST} -p ${_REACTOME_PORT} -n ${_REACTOME_NAME} -u ${_REACTOME_USER} -v ${_REACTOME_PASSWORD} -s ${_SOLR_URL} -e ${_SOLR_USER} -a ${_SOLR_PASSWORD} -i ${_INTERACTORS_DB} -m ${_MAIL_SMTP} -t ${_MAIL_PORT} -f ${_MAIL_DEST} ${_XML} ${_MAIL}; then
        echo "An error occurred during the Solr-Indexer process. Please check logs."
        exit 1
    fi

    echo "Successfully imported data to Solr!"

fi

echo "DONE. Bye!"
