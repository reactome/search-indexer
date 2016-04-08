package uk.ac.ebi.reactome.solr.indexer;

import com.martiansoftware.jsap.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.gk.persistence.MySQLAdaptor;
import org.reactome.server.tools.interactors.database.InteractorsDatabase;
import uk.ac.ebi.reactome.solr.indexer.exception.IndexerException;
import uk.ac.ebi.reactome.solr.indexer.impl.Indexer;
import uk.ac.ebi.reactome.solr.indexer.util.MailUtil;
import uk.ac.ebi.reactome.solr.indexer.util.PreemptiveAuthInterceptor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * Creates the Solr documents and the ebeye.xml file
 * Created by flo on 4/30/14.
 */
public class IndexerTool {

    private static final String FROM = "reactome-indexer@reactome.org";

    public static void main(String[] args) throws JSAPException, SQLException, IndexerException {
        long startTime = System.currentTimeMillis();

        SimpleJSAP jsap = new SimpleJSAP(
                IndexerTool.class.getName(),
                "A tool for generating a Solr Index", //TODO
                new Parameter[]{
                        new FlaggedOption("dbHost",     JSAP.STRING_PARSER,  "localhost",       JSAP.NOT_REQUIRED,  'h', "dbHost",  "The reactome mysql database host"),
                        new FlaggedOption("dbPort",     JSAP.INTSIZE_PARSER, "3306",            JSAP.NOT_REQUIRED,  'p', "dbPort",  "The reactome mysql database port"),
                        new FlaggedOption("dbName",     JSAP.STRING_PARSER,  "reactome",        JSAP.NOT_REQUIRED,  'n', "dbName",  "The reactome mysql database name"),
                        new FlaggedOption("dbUser",     JSAP.STRING_PARSER,  "reactome",        JSAP.REQUIRED,      'u', "dbUser",  "The reactome mysql database user"),
                        new FlaggedOption("dbPw",       JSAP.STRING_PARSER,  JSAP.NO_DEFAULT,   JSAP.REQUIRED,      'v', "dbPw",    "The reactome mysql database password"),
                        new FlaggedOption("solrUrl",    JSAP.STRING_PARSER,  "http://localhost:8983/solr/reactome", JSAP.REQUIRED, 's', "solrUrl", "Url of the running Solr server"),
                        new FlaggedOption("solrUser",   JSAP.STRING_PARSER,  "admin",           JSAP.NOT_REQUIRED,  'e', "solrUser",    "The Solr user"),
                        new FlaggedOption("solrPw",     JSAP.STRING_PARSER,  JSAP.NO_DEFAULT,   JSAP.REQUIRED,      'a', "solrPw",      "The Solr password"),
                        new FlaggedOption("iDbPath",    JSAP.STRING_PARSER,  JSAP.NO_DEFAULT,   JSAP.REQUIRED,      'i', "iDbPath",     "Interactor Database Path"),
                        new FlaggedOption("mailSmtp",   JSAP.STRING_PARSER,  "smtp.oicr.on.ca", JSAP.NOT_REQUIRED,  'm', "mailSmtp",    "SMTP Mail host"),
                        new FlaggedOption("mailPort",   JSAP.INTEGER_PARSER, "25",              JSAP.NOT_REQUIRED,  't', "mailPort",    "SMTP Mail port"),
                        new FlaggedOption("mailDest",   JSAP.STRING_PARSER,  "reactome-developer@reactome.org", JSAP.NOT_REQUIRED, 'f', "mailDest", "Mail Destination"),
                        new QualifiedSwitch("xml",      JSAP.BOOLEAN_PARSER, JSAP.NO_DEFAULT,   JSAP.NOT_REQUIRED,  'x', "xml",         "XML output file for the EBeye")
                }
        );

        JSAPResult config = jsap.parse(args);
        if (jsap.messagePrinted()) System.exit(1);

//        Reactome Mysql database properties
        String dbHost = config.getString("dbHost");
        Integer dbPort = config.getInt("dbPort");
        String dbName = config.getString("dbName");
        String dbUser = config.getString("dbUser");
        String dbPw = config.getString("dbPw");

//        Reactome Solr properties
        String solrUrl = config.getString("solrUrl");
        String solrUser = config.getString("solrUser");
        String solrPw = config.getString("solrPw");

//        Reactome Interactors database properties
        String iDbPath = config.getString("iDbPath");

//        Reactome SMTP properties
        String mailSmtp = config.getString("mailSmtp");
        Integer mailPort = config.getInt("mailPort");
        String mailDest = config.getString("mailDest");

        Boolean xml = config.getBoolean("xml");


        MySQLAdaptor dba = new MySQLAdaptor(dbHost,dbName,dbUser,dbPw,dbPort);
        SolrClient solrClient = getSolrClient(solrUser, solrPw, solrUrl);
        InteractorsDatabase interactorsDatabase = new InteractorsDatabase(iDbPath);
        Indexer indexer = new Indexer(dba, solrClient, xml, interactorsDatabase);
        MailUtil mail = new MailUtil(mailSmtp,mailPort);

        try {
            indexer.index();

            long stopTime = System.currentTimeMillis();
            long ms = stopTime - startTime;

            long hour = TimeUnit.MILLISECONDS.toHours(ms);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(ms));
            long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(ms));

            // Send an error notification by the end of indexer.
//            mail.send(FROM, mailDest, "[SearchIndexer] The Solr indexer has been created", "The Solr Indexer has ended successfully within: " + hour + "hour(s) " + minutes + "minute(s) " + seconds + "second(s) ");

        } catch (IndexerException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            @SuppressWarnings("StringBufferReplaceableByString")
            StringBuilder body = new StringBuilder();
            body.append("The Solr Indexer has not finished properly. Please check the following exception.\n\n");
            body.append("Message: ").append(e.getMessage());
            body.append("\n");
            body.append("Cause: ").append(e.getCause());
            body.append("\n");
            body.append("Stacktrace: ").append(exceptionAsString);

            // Send an error notification by the end of indexer.
//            mail.send(FROM, mailDest, "[SearchIndexer] The Solr indexer has thrown exception", body.toString() );
        }
    }

    private static SolrClient getSolrClient(String user, String password, String url) {

        if (user != null && !user.isEmpty() && password != null && !password.isEmpty()) {
            HttpClientBuilder builder = HttpClientBuilder.create().addInterceptorFirst(new PreemptiveAuthInterceptor());
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, password);
            credentialsProvider.setCredentials(AuthScope.ANY, credentials);
            HttpClient client = builder.setDefaultCredentialsProvider(credentialsProvider).build();
            return new HttpSolrClient(url, client);
        }
        return new HttpSolrClient(url);
    }
}
