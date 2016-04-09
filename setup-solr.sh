#!/usr/bin/env bash


#-----------------------------------------------------------
# Script that automates the Reactome Solr initial setup.
# Execute the files as $sudo ./install_solr.sh -h
#
#
# 19 October 2015
# Florian Korninger - fkorn@ebi.ac.uk
# Guilherme Viteri  - gviteri@ebi.ac.uk
#
#-----------------------------------------------------------

usage="$(basename "$0") -i <password>  [-c <solr_core_name> -d <solr_home> —v <solr_version> -p <port> -u <user> -b <git_branch>] -- program to auto setup the Apache Lucene Solr in Reactome environment.

where:
    -h  Program help/usage

    -a  Install Solr                DEFAULT: false
    -b  Update Solr core            DEFAULT: false
    -c  Import Reactome data        DEFAULT: false

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

    -o  Interactors databae path    DEFAULT: /home/flo/interactors.db

    -p  Mail Smtp server            DEFAULT: smtp.oicr.on.ca
    -q  Mail Smtp port              DEFAULT: 25
    -r  Mail Smtp destination       DEFAULT: reactome-developer@reactome.org

    -s  XML output for EBeye        DEFAULT: false
    -t  Send indexing report mail   DEFAULT: false

    -u  Indexer Github Branch       DEFAULT: master"


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
_SOLR_VERSION="5.5.0"

_INTERACTORS_DB="/home/flo/interactors.db"

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

echo $PWD
echo $(pwd)

if ${_INSTALL_SOLR} = true; then
    if [ -z $_SOLR_PASSWORD ]; then
        echo "missing argument for -i <password>"
        echo "$usage"
        exit 1
    fi;

    sudo service solr stop >/dev/null 2>&1

    echo "Deleting old Solr installed instances"

    sudo rm -r /var/solr >/dev/null 2>&1
    sudo rm -r /opt/solr-*  >/dev/null 2>&1
    sudo rm -r /opt/solr  >/dev/null 2>&1
    sudo rm /etc/init.d/solr >/dev/null 2>&1


    if [ "/" == "${_SOLR_HOME: -1}" ]; then
        _SOLR_HOME=${_SOLR_HOME::-1}
    fi

    if [ "/solr" != "${_SOLR_HOME: -5}" ]; then
        _SOLR_HOME=$_SOLR_HOME"/solr"
    fi

    sudo rm $_SOLR_HOME >/dev/null 2>&1

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

    if [ $_VALID ]; then
        sudo rm /tmp/solr-$_SOLR_VERSION.tgz >/dev/null 2>&1;
        echo "Attempting to download Solr with version: "$_SOLR_VERSION
        wget http://www-eu.apache.org/dist/lucene/solr/$_SOLR_VERSION/solr-$_SOLR_VERSION.tgz -P /tmp
        if [ ! -f /tmp/solr-$_SOLR_VERSION.tgz ]; then
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
    rm -r /tmp/solr-conf >/dev/null 2>&1
    mkdir /tmp/solr-conf

    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/schema.xml -O /tmp/solr-conf/schema.xml
    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/solrconfig.xml -O /tmp/solr-conf/solrconfig.xml
    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/stopwords.txt -O /tmp/solr-conf/stopwords.txt
    wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-conf/prefixstopwords.txt -O /tmp/solr-conf/prefixstopwords.txt

    echo "Removing old Solr core if existing"

    sudo su - solr -c "/opt/solr/bin/solr delete -c reactome" >/dev/null 2>&1

    echo "Creating new Solr core"
    if ! sudo su - solr -c "/opt/solr/bin/solr create_core -c reactome -d /tmp/solr-conf" >/dev/null 2>&1; then
        echo "Could not create new Solr core"
        exit 1;
    fi

    rm -r /tmp/solr-conf >/dev/null 2>&1

    echo "Enabling Solr admin authentication in Jetty"
    sudo wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-jetty-conf/jetty.xml  -O /opt/solr-$_SOLR_VERSION/server/etc/jetty.xml
    sudo wget -q https://raw.githubusercontent.com/reactome/Search/$_GIT_BRANCH/solr-jetty-conf/webdefault.xml  -O /opt/solr-$_SOLR_VERSION/server/etc/webdefault.xml

    sudo bash -c "echo $_SOLR_USER: $_SOLR_PASSWORD,solr-admin > /opt/solr-$_SOLR_VERSION/server/etc/realm.properties"

    echo "Restart solr service..."
    if ! service solr restart >/dev/null 2>&1; then
        echo "Could not restart Solr server"
    fi

    echo "Successfully installed Solr"
fi

if ${_UPDATE_SOLR_CORE} = true; then

    echo "Checking if Solr is running"
    _STATUS=$(curl -H "Content-Type: application/json" --user admin:reactome --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:8983/solr/admin/cores?action=STATUS)
    if [ 200 != "$_STATUS" ]; then
        if ! sudo service solr start >/dev/null 2>&1; then
            echo "Solr is not running and can not be started"
            exit 1;
        fi
    fi

    echo "Checking if Reactome core is available"
    _STATUS=$(curl -H "Content-Type: application/json" --user admin:reactome --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:8983/solr/reactome/admin/ping)
    if [ 200 != "$_STATUS" ]; then
        echo "Reactome core is not available"
        exit 1;
    fi

    echo "Shutting down Solr for updating the core"
    sudo serivce solr stop >/dev/null 2>&1

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
    if ! sudo service solr start >/dev/null 2>&1; then
        echo "Could not start Solr server"
    fi

    echo "Successfully installed Solr"
fi

if ${_IMPORT_DATA} = true; then

    if [ -z $_SOLR_PASSWORD ]; then
        echo "missing argument for -m <password>"
        echo "$usage"
        exit 1
    fi;

    if [ -z $_REACTOME_PASSWORD ]; then
        echo "missing argument for -v <password>"
        echo "$usage"
        exit 1
    fi;

    echo "Checking if Solr is running"
    _STATUS=$(curl -H "Content-Type: application/json" --user admin:reactome --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:8983/solr/admin/cores?action=STATUS)
    if [ 200 != "$_STATUS" ]; then
        if ! sudo service solr start >/dev/null 2>&1; then
            echo "Solr is not running and can not be started"
            exit 1;
        fi
    fi

    echo "Checking if Reactome core is available"
    _STATUS=$(curl -H "Content-Type: application/json" --user admin:reactome --write-out "%{http_code}\n" --silent --output /dev/null http://localhost:8983/solr/reactome/admin/ping)
    if [ 200 != "$_STATUS" ]; then
        echo "Reactome core is not available"
        exit 1;
    fi

    echo "Checking if current directory is valid project"
    if ! mvn -q clean package -DskipTests >/dev/null 2>&1; then
        if [ ! -f /target/Indexer-jar-with-dependencies.jar ]; then
            echo "Cloning new repo from git"
            git clone https://fkorn@bitbucket.org/fkorn/indexer.git
            git -C ./indexer/ fetch && git -C ./indexer/  checkout $_GIT_BRANCH
            _PATH="/indexer"
            echo "Started packaging reactome project"
            if ! mvn -q -f .${_PATH}/pom.xml clean package -DskipTests >/dev/null 2>&1; then
                echo "An error occurred when packaging the project"
                exit 1
            fi
        fi
    fi

    _SOLR_URL=http://localhost:${_SOLR_PORT}/solr/${_SOLR_CORE}

     if ! java -jar .${_PATH}/target/Indexer-jar-with-dependencies.jar -h ${_REACTOME_HOST} -p ${_REACTOME_PORT} -n ${_REACTOME_NAME} -u ${_REACTOME_USER} -v ${_REACTOME_PASSWORD} -s ${_SOLR_URL} -e ${_SOLR_USER} -a ${_SOLR_PASSWORD} -i ${_INTERACTORS_DB} -m ${_MAIL_SMTP} -t ${_MAIL_PORT} -f ${_MAIL_DEST} ${_XML} ${_MAIL}; then
        echo "An error occurred during the dataimport import process"
        exit 1
    fi

    echo "Successfully imported data to Solr"

fi

echo "DONE"