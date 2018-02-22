#!/usr/bin/env bash

#-----------------------------------------------------------
# Script that automates the Reactome Solr initial setup.
# Execute the files as $sudo ./run-indexer.sh -h
#
# Florian Korninger - fkorn@ebi.ac.uk
# Guilherme Viteri  - gviteri@ebi.ac.uk
#
#-----------------------------------------------------------

# Default value
_SOLR_HOME="/var/solr"

_SOLR_CORE="reactome"
_SOLR_PORT=8983
_SOLR_USER="admin"
_SOLR_PASSWORD=""

_NEO4J_HOST="localhost"
_NEO4J_PORT="7474"
_NEO4J_USER="neo4j"
_NEO4J_PASSWORD=""

_GITRAWURL="https://raw.githubusercontent.com"
_GITREPO="reactome"
_GITPROJECT="search-indexer"
_GITBRANCH="master"

_XML=""
_MAIL=""
_MAIL_SMTP="smtp.oicr.on.ca"
_MAIL_PORT="25"
_MAIL_DEST="reactome-developer@reactome.org"

if [[ $(id -u) -ne 0 ]] ; then echo "Please run as sudo." ; exit 1 ; fi


${_NEO4J_HOST} -b ${_NEO4J_PORT} -c ${_NEO4J_USER} -d ${_NEO4J_PASSWORD} -f ${_SOLR_USER} -g ${_SOLR_PASSWORD} -i ${_MAIL_SMTP} -j ${_MAIL_PORT} -k ${_MAIL_DEST} ${_XML} ${_MAIL}

usage () {
    echo "Program to Index Reactome data into SolR"
    echo "usage: sudo ./$(basename "$0") neo4jpass=<neo4j_passwd> solrpass=<solr_passwd> "
    echo "              OPTIONAL
    echo "                       neo4jhost=<neo4j_host>""
    echo "                       neo4jport=<neo4j_port>"
    echo "                       neo4juser=<neo4j_user>"
    echo "                       solruser=<solr_user>"
    echo "                       solrcore=<solr_port>"
    echo "                       solruser=<solr_user>"
    echo "                       gitbranch=<git_branch>"
    echo "                       mailsmtp=<mail_smtp>"
    echo "                       mailport=<mail_port>"
    echo "                       maildest=<mail_destination>"
    echo ""
    echo "   where:"
    echo "       solrpass         REQUIRED"
    echo "       solrcore         DEFAULT: reactome"
    echo "       solruser         DEFAULT: admin"
    echo "       solrport         DEFAULT: 8983"
    echo "       gitbranch        DEFAULT: master (Download Solr configuration from git)"
    echo ""
    echo "e.g sudo ./$(basename "$0") solrpass=not2share"
    echo "e.g sudo ./$(basename "$0") solrpass=not2share solrcore=pathways gitbranch=dev"

    exit
}

# Check arguments
for ARGUMENT in "$@"
do
    KEY=$(echo $ARGUMENT | cut -f1 -d=)
    VALUE=$(echo $ARGUMENT | cut -f2 -d=)

    case "$KEY" in
            solrcore)       _SOLR_CORE=${VALUE} ;;
            solruser)       _SOLR_USER=${VALUE} ;;
            solrpass)       _SOLR_PASSWORD=${VALUE} ;;
            neo4j)          _SOLR_PASSWORD=${VALUE} ;;
            neo4j)          _SOLR_PASSWORD=${VALUE} ;;
            neo4j)          _SOLR_PASSWORD=${VALUE} ;;
            gitbranch)      _GITBRANCH=${VALUE} ;;
            help)           _HELP="help-me" ;;
            -h)             _HELP="help-me" ;;
            *)
    esac
done

if [ "${_HELP}" == "help-me" ]; then
    usage
fi

if [ -z $_SOLR_PASSWORD ]; then
    echo "missing argument for solrpass=<password>"
    exit 1
fi;



# -- Check if neo4j is running and stops program otherwise
# -- If something was wrong with n4j we only knew after a java exception during the program execution
checkNeo4j() {
    _MSG="OK"
    _JSONFILE="query_result.json"
    _NEO4J_URL="http://$_NEO4J_USER:$_NEO4J_PASSWORD@$_NEO4J_HOST:$_NEO4J_PORT/db/data/"
    _STATUS=$(curl -H "Content-Type: application/json" $_NEO4J_URL --write-out "%{http_code}\n" --silent --output $_JSONFILE)
    # no content from the server
    if [ 000 == "$_STATUS" ]; then
        _MSG="Neo4j is not running. Please check 'service neo4j status'"
    # didn't succeed
    elif [ 200 != "$_STATUS" ]; then
        _JSON_MSG=$(cat $_JSONFILE | python -c "import sys, json; print json.load(sys.stdin)['errors'][0]['message']")
        _MSG="Couldn't retrieve neo4j information. Reason [$_JSON_MSG]"
    fi
    if [ -f "$_JSONFILE" ]; then rm $_JSONFILE; fi
    echo "$_MSG"
}

getReleaseInfo() {
    _RELEASE_INFO="Couldn't retrieve DBInfo."
    _JSONFILE="query_result.json"
    _CYPHER='{"statements":[{"statement":"MATCH (n:DBInfo) RETURN n.version LIMIT 1"}]}'
    _NEO4J_URL="http://$_NEO4J_USER:$_NEO4J_PASSWORD@$_NEO4J_HOST:$_NEO4J_PORT/db/data/transaction/commit"
    _STATUS=$(curl -H "Content-Type: application/json" -d "$_CYPHER" $_NEO4J_URL --write-out "%{http_code}\n" --silent --output $_JSONFILE)
    if [ 200 == "$_STATUS" ]; then
        _RELEASE_INFO=v-$(cat $_JSONFILE | sed 's/,//g;s/^.*row...\([0-9]*\).*$/\1/' | tr -d '[:space:]')
    fi
    if [ -f "$_JSONFILE" ]; then
        rm $_JSONFILE
    fi

    echo "$_RELEASE_INFO"
}

# -- Getting neo4j version, also check if neo4j is running and stops program otherwise
getNeo4jVersion() {
    _RET=""
    _JSONFILE="query_result.json"
    _NEO4J_URL="http://$_NEO4J_USER:$_NEO4J_PASSWORD@$_NEO4J_HOST:$_NEO4J_PORT/db/data/"
    _STATUS=$(curl -H "Content-Type: application/json" $_NEO4J_URL --write-out "%{http_code}\n" --silent --output $_JSONFILE)
    if [ 200 == "$_STATUS" ]; then
        _RET=$(cat $_JSONFILE | python -c "import sys, json; print json.load(sys.stdin)['neo4j_version']")
    fi
    if [ -f "$_JSONFILE" ]; then
        rm $_JSONFILE
    fi

    echo "$_RET"
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

    _MSG=$(checkNeo4j)
    if [ "$_MSG" != "OK" ]; then
        echo $_MSG
        exit 1
    fi

    echo "================ Neo4j ==============="
    echo "Neo4j host:         " "http://"$_NEO4J_HOST":"$_NEO4J_PORT
    echo "Neo4j user:         " $_NEO4J_USER
    echo "Neo4j Version:      " $(getNeo4jVersion)
    echo "DB Content:         " $(getReleaseInfo)
    echo "======================================"

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
    if ! mvn -q -U clean package -DskipTests ; then
        if [ ! -f ./target/Indexer-jar-with-dependencies.jar ]; then

            echo "Cloning project from repository..."

            git clone https://github.com/reactome/search-indexer.git

            git -C ./search-indexer/ fetch && git -C ./search-indexer/ checkout $_GIT_BRANCH
            _PATH="/search-indexer"

            echo "Started packaging reactome project"
            if ! mvn -q -f -U .${_PATH}/pom.xml clean package -DskipTests 2>&1 /dev/null; then
               echo "An error occurred when packaging the project."
               exit 1
	          fi
        fi
    fi

    _SOLR_URL=http://localhost:${_SOLR_PORT}/solr/${_SOLR_CORE}

    if ! java -jar .${_PATH}/target/Indexer-jar-with-dependencies.jar -a ${_NEO4J_HOST} -b ${_NEO4J_PORT} -c ${_NEO4J_USER} -d ${_NEO4J_PASSWORD} -e ${_SOLR_URL} -f ${_SOLR_USER} -g ${_SOLR_PASSWORD} -i ${_MAIL_SMTP} -j ${_MAIL_PORT} -k ${_MAIL_DEST} ${_XML} ${_MAIL}; then
        echo "An error occurred during the Solr-Indexer process. Please check logs."
        exit 1
    fi

    echo "Successfully imported data to Solr!"
}

generalSummary () {
   _EBEYE="NO"
   if [ "$_XML" == "-l" ]; then
        _EBEYE="YES";
   fi
   echo "======================================"
   echo "================ SOLR ================"
   echo "======================================"
   echo "SolR Default Home:  " $_SOLR_HOME
   echo "SolR Core:          " $_SOLR_CORE
   echo "SolR Port:          " $_SOLR_PORT
   echo "SolR User:          " $_SOLR_USER
   echo "ebeye.xml:          " $_EBEYE
   echo "SMTP Server:        " $_MAIL_SMTP":"$_MAIL_PORT
   echo "Mail Destination:   " $_MAIL_DEST
   echo "GitHub Branch:      " $_GIT_BRANCH
}

# -- Print variables
generalSummary

runIndexer

echo "DONE. Bye!"
