package org.reactome.server.tools.indexer.swissprot;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

@SuppressWarnings("ALL")
public class SwissProtParser {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    private static String GZ_FILE_URL = "ftp://ftp.uniprot.org/pub/databases/uniprot/current_release/knowledgebase/taxonomic_divisions/uniprot_sprot_human.dat.gz";
    private static String GZ_FILE = "/tmp/uniprot_sprot_human.dat.gz";
    private static String SWISS_PROT_FILE = "/tmp/uniprot_sprot_human.dat";
    private static String SIMPLIFIED_FILE = "/tmp/uniprot_sprot_clean.txt";
    private List<SwissProt> swissProtList;

    public static void main(String[] args) throws Exception {
        SwissProtParser s = new SwissProtParser();
//        s.downloadFile();
//        s.decompressGZIP();
//        s.grep();
        s.parse();
    }

    private void downloadFile() {
        try {
            FileUtils.copyURLToFile(new URL(GZ_FILE_URL), new File(GZ_FILE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void decompressGZIP() throws IOException {
        try (GzipCompressorInputStream in = new GzipCompressorInputStream(new FileInputStream(GZ_FILE))) {
            IOUtils.copy(in, new FileOutputStream(SWISS_PROT_FILE));
        }
    }

    private void grep() throws Exception {
        final String command = "cat " + SWISS_PROT_FILE + " | grep '^AC\\|^ID\\|^GN\\|^\\/\\/'";
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        pb.inheritIO();
        pb.redirectOutput(new File(SIMPLIFIED_FILE));
        final File errorFile = new File("/tmp/redirectError.err");
        errorFile.deleteOnExit();
        pb.redirectError(errorFile);
        Process p = pb.start();
        p.waitFor();
        if (p.exitValue() != 0 || errorFile.length() > 0) {
            throw new Exception("Could not create the simplified file. Reason: " + FileUtils.readFileToString(errorFile, Charset.defaultCharset()).trim());
        }
    }

    private void parse() throws IOException {
        swissProtList = new ArrayList<>();
        List<String> lines = FileUtils.readLines(new File(SIMPLIFIED_FILE), Charset.defaultCharset());
        SwissProt swissProt = new SwissProt();
        for (String line : lines) {
            if(!line.equals("//")) {
                //System.out.println(line);
                String[] entry = line.split("\\s+");

                String values = "";
                //System.out.println(values);
                if (entry[0].startsWith("ID")) {
                    // ID   ZZEF1_HUMAN             Reviewed;        2961 AA.
                    values = entry[0] + ": " + entry[1].trim();
                    swissProt.setId("");
                }

                if (entry[0].startsWith("AC")) {
                    // AC   O43149; A7MBM5; Q6NXG0; Q6ZRA1; Q6ZSF4; Q9NVB9;
//                swissProt.addAccession("acc");
                    for (int i = 1; i < entry.length; i++) {
                        values = entry[0] + " DD : " + entry[i];
                        //System.out.println(values);
                    }
                    values = entry[0] + ": " + entry[1];
                }
                System.out.println("----");
                if (entry[0].startsWith("GN")) {
                    Pattern pattern = Pattern.compile("=.*?;");
                    Matcher matcher = pattern.matcher(line);
                    // check all occurance
                    while (matcher.find()) {
                        System.out.println(line);
                        System.out.println(matcher.group());
                    }

                    // GN   Name=ZZEF1; Synonyms=KIAA0399;
//                swissProt.setId();
                    values = entry[0] + ": " + entry[1];
                }

               // System.out.println(values);
            } else {
                swissProtList.add(swissProt);
                swissProt = new SwissProt();
            }
        }
    }
}