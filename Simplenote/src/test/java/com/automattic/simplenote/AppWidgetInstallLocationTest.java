package com.automattic.simplenote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

public class AppWidgetInstallLocationTest {
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String APPWIDGET_UPDATE = "android.appwidget.action.APPWIDGET_UPDATE";
    private static final String MANIFEST_PATH = "src/main/AndroidManifest.xml";

    @Test
    public void widgetAppLeavesInstallLocationUnspecified() throws Exception {
        Document manifest = parseManifest();

        assertTrue(hasAppWidgetReceiver(manifest));
        assertFalse(manifest.getDocumentElement().hasAttributeNS(ANDROID_NAMESPACE, "installLocation"));
    }

    private static Document parseManifest() throws Exception {
        File file = new File(MANIFEST_PATH);
        if (!file.exists()) {
            file = new File("Simplenote", MANIFEST_PATH);
        }
        assertTrue("missing " + MANIFEST_PATH, file.exists());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file);
    }

    private static boolean hasAppWidgetReceiver(Document manifest) {
        NodeList receivers = manifest.getElementsByTagName("receiver");
        for (int receiverIndex = 0; receiverIndex < receivers.getLength(); receiverIndex++) {
            Element receiver = (Element) receivers.item(receiverIndex);
            NodeList actions = receiver.getElementsByTagName("action");
            for (int actionIndex = 0; actionIndex < actions.getLength(); actionIndex++) {
                Element action = (Element) actions.item(actionIndex);
                if (APPWIDGET_UPDATE.equals(action.getAttributeNS(ANDROID_NAMESPACE, "name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
