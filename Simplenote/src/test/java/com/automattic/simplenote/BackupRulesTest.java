package com.automattic.simplenote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

public class BackupRulesTest {
    private static final String SIMPERIUM_PREFS = "simperium.xml";

    @Test
    public void legacyBackupRulesExcludeSimperiumSessionPreferences() throws Exception {
        Document rules = parse("src/main/res/xml/backup_rules.xml");

        assertEquals("full-backup-content", rules.getDocumentElement().getTagName());
        assertTrue(hasSharedPrefExclusion(rules.getDocumentElement()));
    }

    @Test
    public void dataExtractionRulesExcludeSimperiumSessionPreferencesFromBothTransports() throws Exception {
        Document rules = parse("src/main/res/xml/data_extraction_rules.xml");

        assertEquals("data-extraction-rules", rules.getDocumentElement().getTagName());
        for (String transport : new String[]{"cloud-backup", "device-transfer"}) {
            NodeList sections = rules.getElementsByTagName(transport);
            assertEquals(transport, 1, sections.getLength());
            assertTrue(transport, hasSharedPrefExclusion((Element) sections.item(0)));
        }
    }

    private static Document parse(String modulePath) throws Exception {
        File file = new File(modulePath);
        if (!file.exists()) {
            file = new File("Simplenote", modulePath);
        }
        assertTrue("missing " + modulePath, file.exists());
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
    }

    private static boolean hasSharedPrefExclusion(Element section) {
        NodeList excludes = section.getElementsByTagName("exclude");
        for (int index = 0; index < excludes.getLength(); index++) {
            Node exclude = excludes.item(index);
            if (exclude.getParentNode() != section) {
                continue;
            }
            Element element = (Element) exclude;
            if ("sharedpref".equals(element.getAttribute("domain"))
                    && SIMPERIUM_PREFS.equals(element.getAttribute("path"))) {
                return true;
            }
        }
        return false;
    }
}
