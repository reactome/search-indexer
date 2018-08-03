package org.reactome.server.tools.indexer.icon.parser;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.reactome.server.tools.indexer.icon.model.Icon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class MetadataParser {
    private static final Logger parserLogger = LoggerFactory.getLogger("parserLogger");
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    private static MetadataParser instance;
    private String iconsDir;
    private String ehldsDir;
    private List<Icon> icons = new ArrayList<>();

    private MetadataParser(String iconsDir, String ehldsDir) {
        if (StringUtils.isEmpty(iconsDir) || StringUtils.isEmpty(ehldsDir)) {
            throw new IllegalArgumentException("Icons directory or EHLDs directory can't be null or empty.");
        }

        this.iconsDir = iconsDir;
        this.ehldsDir = ehldsDir;
    }

    public static void main(String[] args) {
        String iconsDir = "/Users/reactome/Dev/icons/icon-lib/lib";
        String ehldsDir = "/Users/reactome/Dev/icons/ehld";
        MetadataParser metadataParser = MetadataParser.getInstance(iconsDir, ehldsDir);
        List<Icon> icons = metadataParser.getIcons();
        icons.forEach(System.out::println);
    }

    public static MetadataParser getInstance(String iconsDir, String ehldDir) {
        if (instance == null) {
            instance = new MetadataParser(iconsDir, ehldDir);
        }
        return instance;
    }

    private void parse() {
        long startParse = System.currentTimeMillis();
        File iconLibDir = new File(iconsDir);
        if (!iconLibDir.exists()) {
            logger.error("Cannot find folder: {}", iconsDir);
            System.exit(1);
        }

        Collection<File> files = FileUtils.listFiles(iconLibDir, new String[]{"xml"}, true);
        logger.info("Parsing " + files.size() + " icons");
        AtomicLong id = new AtomicLong(1L);
        files.parallelStream().forEach(xml -> {
            String fileNameWithoutExtension = FilenameUtils.removeExtension(xml.getName());
            String group = xml.getParentFile().getName();
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(Icon.class);
                Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
                Icon icon = (Icon) jaxbUnmarshaller.unmarshal(xml);
                icon.setId(id.getAndIncrement());
                icon.setName(fileNameWithoutExtension);
                icon.setGroup(group);
                icon.setEhlds(getEhlds(group, fileNameWithoutExtension));
                icons.add(icon);
            } catch (JAXBException e) {
                e.printStackTrace();
                logger.error("Could not unmarshall file: {}", xml.getPath());
            }
        });
        logger.info("Parsing has finished and it took {}.", (System.currentTimeMillis() - startParse) + ".ms");
    }

    /**
     * @param fileNameWithoutExtension File name only
     * @return stIds where the icon is present
     */
    private List<String> getEhlds(String group, String fileNameWithoutExtension) {
        List<String> ehlds = new ArrayList<>();
        String escapedFileName = StringEscapeUtils.escapeXml11(fileNameWithoutExtension);

        final String command = "grep -i -l -E 'id=\"" + escapedFileName + "\"|data-name=\"" + escapedFileName + "\"' "+ ehldsDir + "/*.svg | awk -F/ '{print $NF}'";
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        try {
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String readline;
            while ((readline = reader.readLine()) != null) {
                if (readline.startsWith("R-")) ehlds.add(readline.trim().replace(".svg", ""));
            }
            p.destroyForcibly();
        } catch (IOException e) {
            parserLogger.error("Error while getting the EHLDs for the file {}/{}", group, fileNameWithoutExtension);
        }

        if (ehlds.isEmpty()) parserLogger.info("{}/{} not found in any EHLD", group, fileNameWithoutExtension);

        return ehlds;
        // TODO ARROW are not named as indication arrow, process arrow - they are just arrow
    }

    /**
     * Also invoke the parser, if icons list is Empty.
     *
     * @return icons
     */
    public List<Icon> getIcons() {
        if (icons == null || icons.isEmpty()) parse();
        return icons;
    }
}
