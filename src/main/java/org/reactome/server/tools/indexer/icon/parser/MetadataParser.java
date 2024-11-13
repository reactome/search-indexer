package org.reactome.server.tools.indexer.icon.parser;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.reactome.server.tools.indexer.icon.model.Category;
import org.reactome.server.tools.indexer.icon.model.Icon;
import org.reactome.server.tools.indexer.icon.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class MetadataParser {
    private static final Logger parserLogger = LoggerFactory.getLogger("parserLogger");
    private static final Logger logger = LoggerFactory.getLogger("importLogger");
    private static final SortedSet<String> parserMessages = new TreeSet<>();

    private static MetadataParser instance;
    private final String iconsDir;
    private final String ehldsDir;
    private final List<Icon> icons = new ArrayList<>();
    private final Map<String, Set<Icon>> ehldToIcons = new ConcurrentHashMap<>();

    private MetadataParser(String iconsDir, String ehldsDir) {
        if (StringUtils.isEmpty(iconsDir) || StringUtils.isEmpty(ehldsDir))
            throw new IllegalArgumentException("Icons directory or EHLDs directory can't be null or empty.");

        if (!new File(ehldsDir).exists() || !new File(iconsDir).exists())
            throw new IllegalArgumentException("Icons directory or EHLDs directory doesn't exist");

        this.iconsDir = iconsDir;
        this.ehldsDir = ehldsDir;
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

        Collection<File> files = FileUtils.listFiles(iconLibDir, new String[]{"xml"}, true);
        logger.info("Parsing " + files.size() + " icons");
        logger.info("Start unmarshalling xml metadata");
        files.forEach(xml -> {
            String stId = FilenameUtils.removeExtension(xml.getName());
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(Icon.class);
                Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
                Icon icon = (Icon) jaxbUnmarshaller.unmarshal(xml);
                icon.setId(stId.replaceAll("R-ICO-", ""));
                icon.setStId(stId);
                assignSpecies(icon);
                validateReferences(icon);
                icons.add(icon);
            } catch (JAXBException e) {
                e.printStackTrace();
                logger.error("Could not unmarshall file: {}", xml.getPath());
            } catch (InvalidObjectException e) {
                logger.error(e.getMessage());
            }
        });

        logger.info("Unmarshalling is completed");
        logger.info("Getting the EHLDs where the icon is present");
        // Get EHLDs that the icon is in.
        icons.parallelStream().forEach(icon -> icon.setEhlds(getEhlds(icon)));
        logger.info("Parsing has finished and it took {}.", (System.currentTimeMillis() - startParse) + ".ms");
        logger.info("Updating SVGs by adding classes to icons based on categories");
        insertCategoriesAsClassesInSVG();
        logger.info("Updating SVGs done");
        parserMessages.forEach(parserLogger::info);
    }

    /**
     * Species are set programmatically based on the given categories
     *
     * @param icon given icon
     */
    private void assignSpecies(Icon icon) {
        if (icon.getCategories() == null || icon.getCategories().isEmpty())
            throw new IllegalArgumentException("Couldn't assign species. Category is missing.");

        icon.setSpecies("Homo sapiens");
        if (icon.getCategories().contains(new Category("arrow")) || icon.getCategories().contains(new Category("compound"))) {
            icon.setSpecies("Entries without species");
        }
    }

    private void validateReferences(Icon icon) throws InvalidObjectException {
        List<Reference> references = icon.getReferences();
        if (references == null) {
            logger.error("The file " + icon.getStId() + " (category: " + icon.getCategories().toString() + ") doesn't contain any reference.");
        } else {
            for (Reference reference : references) {
                if ((reference.getDb().equalsIgnoreCase("UNIPROT") ||
                        reference.getDb().equalsIgnoreCase("KEGG") ||
                        reference.getDb().equalsIgnoreCase("MESH") ||
                        reference.getDb().equalsIgnoreCase("INTERPRO")) && reference.getId().contains(":")) {
                    throw new InvalidObjectException("Potential Invalid reference. It contains colon where it shouldn't have [File: " + icon.getStId() + " RefId: " + reference.getId() + ", RefDB: " + reference.getDb() + "]");
                }
            }
        }
    }

    /**
     * @param icon the icon
     * @return stIds where the icon is present
     */
    private List<String> getEhlds(Icon icon) {
        List<String> ehlds = new ArrayList<>();
        String escapedFileName = StringEscapeUtils.escapeXml11(icon.getStId());
        String quotedFilename = Pattern.quote(escapedFileName);
        // Grep command works differently in Linux and Mac, so we need to detect OS and apply the proper grep command
        // TODO: Maybe look into using grep4j or unix4j. In general, it would be nice if we could avoid system calls.
        String os = System.getProperty("os.name").toLowerCase();
        // Sometime Adobe Illustrator randomly adds (_) underline after the icon id, this regex covers that.
        String command = "grep -i -l -###OS### 'id=\"" + quotedFilename + "_*\"|data-name=\"" + quotedFilename + "\"' " + ehldsDir + "/*.svg | awk -F/ '{print $NF}'";
        String grepPattern = "P"; // works on linux
        if (os.contains("mac")) grepPattern = "E"; // works on MacOS
        command = command.replace("###OS###", grepPattern);
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        try {
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String readline;
            while ((readline = reader.readLine()) != null) {
                if (readline.startsWith("R-")) {
                    String svgFile = readline.trim();
                    ehlds.add(svgFile.replace(".svg", ""));
                    // Adding icon to the EHLD listing to then modify it to add categories as classes
                    ehldToIcons.computeIfAbsent(svgFile, k -> Collections.synchronizedSet(new HashSet<>())).add(icon);
                }
            }
            p.destroyForcibly();
        } catch (IOException e) {
            e.printStackTrace();
            //parserLogger.error("Error while getting the EHLDs for the file {}/{}", group, fileNameWithoutExtension);
        }

        if (ehlds.isEmpty()) {
            if (icon.isSkip() == null || !icon.isSkip()) {
                parserMessages.add(icon.getStId() + " (" + icon.getName() + ")");
            }
        } else if (icon.isSkip() != null && icon.isSkip()) {
            parserMessages.add("This is flagged to be skipped and was found in a EHLD, please fix its metadata: " + icon.getStId());
        }

        return ehlds;
    }

    public void insertCategoriesAsClassesInSVG() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Write the modified document back to the file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();

        // Parse the SVG file into a DOM document
        this.ehldToIcons.entrySet().parallelStream().forEach((entry) -> {
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                String svgPath = ehldsDir + "/" + entry.getKey();
                Document document = builder.parse(svgPath);
                ArrayList<Icon> iconsOfEhld = new ArrayList<>(entry.getValue());
                iterateAndModify(document.getDocumentElement(), iconsOfEhld.stream()
                        .collect(Collectors.toMap(
                                icon -> Pattern.compile(icon.getStId() + "(_[0-9_]*)?"),
                                icon -> icon.getCategories().stream()
                                        .map(Category::getName)
                                        .collect(Collectors.toList())
                        ))
                );
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "no");
                transformer.transform(new DOMSource(document), new StreamResult(svgPath));
            } catch (SAXException | IOException | ParserConfigurationException | TransformerException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // Recursive method to iterate over all nodes
    private static void iterateAndModify(Node node, Map<Pattern, List<String>> iconIdToClasses) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;

            // Check for 'id' or 'data-name' attribute and match with regex
            String id = element.getAttribute("id");
            String dataName = element.getAttribute("data-name");

            for (Map.Entry<Pattern, List<String>> entry : iconIdToClasses.entrySet()) {
                Pattern pattern = entry.getKey();
                List<String> newClasses = entry.getValue();

                if (pattern.matcher(id).matches() || pattern.matcher(dataName).matches()) {
                    Set<String> classes = Stream.of(element.getAttribute("class").trim().split(" "))
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());
                    classes.addAll(newClasses);
                    element.setAttribute("class", String.join(" ", classes));
                    break;
                }
            }

            // Recurse through all child nodes
            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                iterateAndModify(childNodes.item(i), iconIdToClasses);
            }
        }
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
