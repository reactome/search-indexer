package org.reactome.server.tools.indexer;

import com.martiansoftware.jsap.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.reactome.server.interactors.database.InteractorsDatabase;
import org.reactome.server.tools.indexer.config.IndexerNeo4jConfig;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.impl.NewIndexer;
import org.reactome.server.tools.indexer.util.MailUtil;
import org.reactome.server.tools.indexer.util.PreemptiveAuthInterceptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;


/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Component
public class Main {

    private static final String FROM = "reactome-indexer@reactome.org";
    private static final String DEF_MAIL_DEST = "reactome-developer@reactome.org";
    private static final String DEF_MAIL_SMTP = "smtp.oicr.on.ca";
    private static final String DEF_SOLR_URL = "http://localhost:8983/solr/reactome";
    private static final String MAIL_SUBJECT_SUCCESS = "[Search Indexer] The Solr indexer has been created";
    private static final String MAIL_SUBJECT_ERROR = "[SearchIndexer] The Solr indexer has thrown exception";

    public static void main(String[] args) throws JSAPException, SQLException {

        long startTime = System.currentTimeMillis();

        SimpleJSAP jsap = new SimpleJSAP(Main.class.getName(), "A tool for generating a Solr Index.",
                new Parameter[]{
                        new FlaggedOption("host",       JSAP.STRING_PARSER,     "localhost",        JSAP.NOT_REQUIRED,  'a', "host",        "The neo4j host"),
                        new FlaggedOption("port",       JSAP.STRING_PARSER,     "7474",             JSAP.NOT_REQUIRED,  'b', "port",        "The neo4j port"),
                        new FlaggedOption("user",       JSAP.STRING_PARSER,     "neo4j",            JSAP.NOT_REQUIRED,  'c', "user",        "The neo4j user"),
                        new FlaggedOption("password",   JSAP.STRING_PARSER,     JSAP.NO_DEFAULT,    JSAP.REQUIRED,      'd', "password",    "The neo4j password"),
                        new FlaggedOption("solrUrl",    JSAP.STRING_PARSER,     DEF_SOLR_URL,       JSAP.REQUIRED,      'e', "solrUrl",     "Url of the running Solr server"),
                        new FlaggedOption("solrUser",   JSAP.STRING_PARSER,     "admin",            JSAP.NOT_REQUIRED,  'f', "solrUser",    "The Solr user"),
                        new FlaggedOption("solrPw",     JSAP.STRING_PARSER,     JSAP.NO_DEFAULT,    JSAP.REQUIRED,      'g', "solrPw",      "The Solr password"),
                        new FlaggedOption("iDbPath",    JSAP.STRING_PARSER,     JSAP.NO_DEFAULT,    JSAP.REQUIRED,      'h', "iDbPath",     "Interactor Database Path"),
                        new FlaggedOption("mailSmtp",   JSAP.STRING_PARSER,     DEF_MAIL_SMTP,      JSAP.NOT_REQUIRED,  'i', "mailSmtp",    "SMTP Mail host"),
                        new FlaggedOption("mailPort",   JSAP.INTEGER_PARSER,    "25",               JSAP.NOT_REQUIRED,  'j', "mailPort",    "SMTP Mail port"),
                        new FlaggedOption("mailDest",   JSAP.STRING_PARSER,     DEF_MAIL_DEST,      JSAP.NOT_REQUIRED,  'k', "mailDest",    "Mail Destination"),
                        new QualifiedSwitch("xml",      JSAP.BOOLEAN_PARSER,    JSAP.NO_DEFAULT,    JSAP.NOT_REQUIRED,  'l', "xml",         "XML output file for the EBeye"),
                        new QualifiedSwitch("mail",     JSAP.BOOLEAN_PARSER,    JSAP.NO_DEFAULT,    JSAP.NOT_REQUIRED,  'm', "mail",        "Activates mail option")
                }
        );

        JSAPResult config = jsap.parse(args);
        if (jsap.messagePrinted()) System.exit(1);

        //  Reactome Solr properties for solr connection
        SolrClient solrClient = getSolrClient(config.getString("solrUser"), config.getString("solrPw"), config.getString("solrUrl"));

        // Reactome Mail properties
        Boolean mail = config.getBoolean("mail");
        String mailDest = config.getString("mailDest");
        MailUtil mailUtil = new MailUtil(config.getString("mailSmtp"), config.getInt("mailPort"));

        // Neo4j
        AnnotationConfigApplicationContext ctx = getNeo4jContext(config.getString("host"), config.getString("port"), config.getString("user"), config.getString("password"));

        // Instantiate NewIndexer class as Bean. Spring managed class.
        // Passing arguments using the constructor wasn't working somehow.
        NewIndexer indexer = ctx.getBean(NewIndexer.class);
        indexer.setSolrClient(solrClient);
        indexer.setXml(config.getBoolean("xml")); // ebeye.xml file
        indexer.setInteractorsDatabase(new InteractorsDatabase(config.getString("iDbPath")));

        try {
            indexer.index();

            if (mail) {
                long stopTime = System.currentTimeMillis();
                long ms = stopTime - startTime;
                long hour = TimeUnit.MILLISECONDS.toHours(ms);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(ms));
                long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(ms));
                // Send an notification by the end of indexing.
                mailUtil.send(FROM, mailDest, MAIL_SUBJECT_SUCCESS, "The Solr Indexer has ended successfully within: " + hour + "hour(s) " + minutes + "minute(s) " + seconds + "second(s) ");
            }
        } catch (IndexerException e) {
            if (mail) {
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
                mailUtil.send(FROM, mailDest, MAIL_SUBJECT_ERROR, body.toString());
            }
        }
    }

    /**
     * Get solr connection using authentication
     *
     * @param user     solr user
     * @param password solr password
     * @param url      solr url
     * @return solr connection
     */
    private static SolrClient getSolrClient(String user, String password, String url) {

        if (user != null && !user.isEmpty() && password != null && !password.isEmpty()) {
            HttpClientBuilder builder = HttpClientBuilder.create().addInterceptorFirst(new PreemptiveAuthInterceptor());
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, password);
            credentialsProvider.setCredentials(AuthScope.ANY, credentials);
            HttpClient client = builder.setDefaultCredentialsProvider(credentialsProvider).build();
            return new HttpSolrClient.Builder(url).withHttpClient(client).build();
        }

        return new HttpSolrClient.Builder(url).build();
    }

    /**
     * Based on the arguments, set systemProperties and get a Neo4j context that already holds the connection with Neo4j
     *
     * @param host     neo4j host
     * @param port     neo4j port
     * @param user     neo4j user
     * @param password neo4j password
     * @return the applicationContext managed by Spring
     */
    private static AnnotationConfigApplicationContext getNeo4jContext(String host, String port, String user, String password) {
        // Set system properties that will be used by IndexerNeo4jConfig
        System.setProperty("neo4j.host", host);
        System.setProperty("neo4j.port", port);
        System.setProperty("neo4j.user", user);
        System.setProperty("neo4j.password", password);

        return new AnnotationConfigApplicationContext(IndexerNeo4jConfig.class); // Use annotated beans from the specified package

    }
}
