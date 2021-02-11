#!/bin/bash
set -e 

chmod g+w sitemap_*
chown www-data:reactome sitemap*           
mv sitemapindex.xml /usr/local/reactomes/Reactome/production/Website/static/ 
mv sitemap_* /usr/local/reactomes/Reactome/production/Website/static/sitemap/ 
