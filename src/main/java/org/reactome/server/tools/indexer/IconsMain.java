package org.reactome.server.tools.indexer;

import com.martiansoftware.jsap.*;
import org.apache.solr.client.solrj.SolrClient;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.icon.exporter.IconsExporter;
import org.reactome.server.tools.indexer.icon.impl.IconIndexer;
import org.springframework.stereotype.Component;

import static org.reactome.server.tools.indexer.util.SolrUtility.closeSolrServer;
import static org.reactome.server.tools.indexer.util.SolrUtility.getSolrClient;

/**
 * Simple main class only to index targets
 *
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Component
public class IconsMain {
    private static final String DEF_SOLR_URL = "http://localhost:8983/solr";
    private static final String DEF_SOLR_CORE = "reactome";

    public static void main(String[] args) throws IndexerException, JSAPException {
        try {
            SimpleJSAP jsap = new SimpleJSAP(IconsMain.class.getName(), "A tool for generating a Solr Index for Icons.",
                    new Parameter[]{
                            new FlaggedOption("solrUrl", JSAP.STRING_PARSER, DEF_SOLR_URL, JSAP.REQUIRED, 'a', "solrUrl", "Url of the running Solr server"),
                            new FlaggedOption("solrCollection", JSAP.STRING_PARSER, DEF_SOLR_CORE, JSAP.REQUIRED, 'b', "solrCollection", "The Reactome solr collection"),
                            new FlaggedOption("solrUser", JSAP.STRING_PARSER, "admin", JSAP.NOT_REQUIRED, 'c', "solrUser", "The Solr user"),
                            new FlaggedOption("solrPw", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'd', "solrPw", "The Solr password"),
                            new FlaggedOption("iconsDir", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'e', "iconsDir", "The Solr user"),
                            new FlaggedOption("ehldDir", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'f', "ehldDir", "The Solr user"),
                            new FlaggedOption("outputDir", JSAP.STRING_PARSER, ".", JSAP.NOT_REQUIRED, 'g', "outputDir", "The icons mapping file output directory")
                    }
            );

            JSAPResult config = jsap.parse(args);
            if (jsap.messagePrinted()) System.exit(1);

            SolrClient solrClient = getSolrClient(config.getString("solrUser"), config.getString("solrPw"), config.getString("solrUrl"));

            IconIndexer indexer = new IconIndexer();
            indexer.setSolrClient(solrClient);
            indexer.setIconsDir(config.getString("iconsDir"));
            indexer.setEhldsDir(config.getString("ehldDir"));
            indexer.index();
            closeSolrServer(solrClient);

            IconsExporter tsvWriter = new IconsExporter(solrClient, config.getString("solrCollection"));
            tsvWriter.write(config.getString("outputDir"));
        } catch (Error | Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }
}
