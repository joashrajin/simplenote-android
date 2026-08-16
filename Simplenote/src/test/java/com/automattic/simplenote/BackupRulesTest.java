package com.automattic.simplenote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.automattic.simplenote.utils.WordPressTokenStore;

import org.mockito.MockedStatic;
import org.wordpress.passcodelock.PasscodePreferenceStore;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;

public class BackupRulesTest {
    private static final String SIMPERIUM_PREFS = "simperium.xml";
    private static final String WORDPRESS_PREFS = "wordpress_auth.xml";
    private static final String[] PASSCODE_PREFS = {"passcode_lock.xml", "passcode_lock.xml.bak"};
    private static final String[] TRACKS_DATABASE_PATHS = {
        "tracks.db", "tracks.db-journal", "tracks.db-wal", "tracks.db-shm"
    };

    @Test
    public void legacyBackupRulesExcludeSimperiumSessionPreferences() throws Exception {
        Document rules = parse("src/main/res/xml/backup_rules.xml");

        assertEquals("full-backup-content", rules.getDocumentElement().getTagName());
        assertTrue(hasExclusion(rules.getDocumentElement(), "sharedpref", SIMPERIUM_PREFS));
    }

    @Test
    public void dataExtractionRulesExcludeSimperiumSessionPreferencesFromBothTransports() throws Exception {
        Document rules = parse("src/main/res/xml/data_extraction_rules.xml");

        assertEquals("data-extraction-rules", rules.getDocumentElement().getTagName());
        for (String transport : new String[]{"cloud-backup", "device-transfer"}) {
            NodeList sections = rules.getElementsByTagName(transport);
            assertEquals(transport, 1, sections.getLength());
            assertTrue(transport, hasExclusion((Element) sections.item(0), "sharedpref", SIMPERIUM_PREFS));
        }
    }

    @Test
    public void legacyBackupRulesExcludeWordPressTokenStore() throws Exception {
        Document rules = parse("src/main/res/xml/backup_rules.xml");

        assertTrue(hasExclusion(rules.getDocumentElement(), "sharedpref", WORDPRESS_PREFS));
    }

    @Test
    public void dataExtractionRulesExcludeWordPressTokenStoreFromBothTransports() throws Exception {
        Document rules = parse("src/main/res/xml/data_extraction_rules.xml");

        for (String transport : new String[]{"cloud-backup", "device-transfer"}) {
            Element section = (Element) rules.getElementsByTagName(transport).item(0);
            assertTrue(transport, hasExclusion(section, "sharedpref", WORDPRESS_PREFS));
        }
    }

    @Test
    public void legacyBackupRulesExcludePasscodeStoreAndRecoveryFile() throws Exception {
        Document rules = parse("src/main/res/xml/backup_rules.xml");

        for (String path : PASSCODE_PREFS) {
            assertTrue(path, hasExclusion(rules.getDocumentElement(), "sharedpref", path));
        }
    }

    @Test
    public void dataExtractionRulesExcludePasscodeStoreFromBothTransports() throws Exception {
        Document rules = parse("src/main/res/xml/data_extraction_rules.xml");

        for (String transport : new String[]{"cloud-backup", "device-transfer"}) {
            Element section = (Element) rules.getElementsByTagName(transport).item(0);
            for (String path : PASSCODE_PREFS) {
                assertTrue(transport + "/" + path, hasExclusion(section, "sharedpref", path));
            }
        }
    }

    @Test
    public void fullBackupPreparesBothSecretStoresBeforeFailingClosed() {
        SimplenoteBackupAgent agent = new SimplenoteBackupAgent();
        WordPressTokenStore tokenStore = mock(WordPressTokenStore.class);
        PasscodePreferenceStore passcodeStore = mock(PasscodePreferenceStore.class);
        when(tokenStore.prepareForBackup()).thenReturn(false);
        when(passcodeStore.prepareForBackup()).thenReturn(true);

        try (MockedStatic<WordPressTokenStore> tokenStores = mockStatic(WordPressTokenStore.class);
             MockedStatic<PasscodePreferenceStore> passcodeStores = mockStatic(PasscodePreferenceStore.class)) {
            tokenStores.when(() -> WordPressTokenStore.from(agent)).thenReturn(tokenStore);
            passcodeStores.when(() -> PasscodePreferenceStore.from(agent)).thenReturn(passcodeStore);

            assertThrows(IOException.class, () -> agent.onFullBackup(null));

            verify(tokenStore).prepareForBackup();
            verify(passcodeStore).prepareForBackup();
        }
    }

    @Test
    public void fullBackupFailsWhenPasscodeStateCannotBePrepared() {
        SimplenoteBackupAgent agent = new SimplenoteBackupAgent();
        WordPressTokenStore tokenStore = mock(WordPressTokenStore.class);
        PasscodePreferenceStore passcodeStore = mock(PasscodePreferenceStore.class);
        when(tokenStore.prepareForBackup()).thenReturn(true);
        when(passcodeStore.prepareForBackup()).thenReturn(false);

        try (MockedStatic<WordPressTokenStore> tokenStores = mockStatic(WordPressTokenStore.class);
             MockedStatic<PasscodePreferenceStore> passcodeStores = mockStatic(PasscodePreferenceStore.class)) {
            tokenStores.when(() -> WordPressTokenStore.from(agent)).thenReturn(tokenStore);
            passcodeStores.when(() -> PasscodePreferenceStore.from(agent)).thenReturn(passcodeStore);

            assertThrows(IOException.class, () -> agent.onFullBackup(null));

            verify(tokenStore).prepareForBackup();
            verify(passcodeStore).prepareForBackup();
        }
    }

    @Test
    public void fullBackupStillPreparesPasscodeWhenTokenPreparationThrows() {
        SimplenoteBackupAgent agent = new SimplenoteBackupAgent();
        WordPressTokenStore tokenStore = mock(WordPressTokenStore.class);
        PasscodePreferenceStore passcodeStore = mock(PasscodePreferenceStore.class);
        when(tokenStore.prepareForBackup()).thenThrow(new IllegalStateException("token"));
        when(passcodeStore.prepareForBackup()).thenReturn(true);

        try (MockedStatic<WordPressTokenStore> tokenStores = mockStatic(WordPressTokenStore.class);
             MockedStatic<PasscodePreferenceStore> passcodeStores = mockStatic(PasscodePreferenceStore.class)) {
            tokenStores.when(() -> WordPressTokenStore.from(agent)).thenReturn(tokenStore);
            passcodeStores.when(() -> PasscodePreferenceStore.from(agent)).thenReturn(passcodeStore);

            assertThrows(IOException.class, () -> agent.onFullBackup(null));

            verify(passcodeStore).prepareForBackup();
        }
    }

    @Test
    public void restoreFinishedClearsBothSecretStores() {
        SimplenoteBackupAgent agent = new SimplenoteBackupAgent();
        WordPressTokenStore tokenStore = mock(WordPressTokenStore.class);
        PasscodePreferenceStore passcodeStore = mock(PasscodePreferenceStore.class);

        try (MockedStatic<WordPressTokenStore> tokenStores = mockStatic(WordPressTokenStore.class);
             MockedStatic<PasscodePreferenceStore> passcodeStores = mockStatic(PasscodePreferenceStore.class)) {
            tokenStores.when(() -> WordPressTokenStore.from(agent)).thenReturn(tokenStore);
            passcodeStores.when(() -> PasscodePreferenceStore.from(agent)).thenReturn(passcodeStore);

            agent.onRestoreFinished();

            verify(tokenStore).clearTokenWithRetry();
            verify(passcodeStore).clearPasscodeWithRetry();
        }
    }

    @Test
    public void restoreStillClearsPasscodeWhenTokenCleanupThrows() {
        SimplenoteBackupAgent agent = new SimplenoteBackupAgent();
        WordPressTokenStore tokenStore = mock(WordPressTokenStore.class);
        PasscodePreferenceStore passcodeStore = mock(PasscodePreferenceStore.class);
        when(tokenStore.clearTokenWithRetry()).thenThrow(new IllegalStateException("token"));

        try (MockedStatic<WordPressTokenStore> tokenStores = mockStatic(WordPressTokenStore.class);
             MockedStatic<PasscodePreferenceStore> passcodeStores = mockStatic(PasscodePreferenceStore.class)) {
            tokenStores.when(() -> WordPressTokenStore.from(agent)).thenReturn(tokenStore);
            passcodeStores.when(() -> PasscodePreferenceStore.from(agent)).thenReturn(passcodeStore);

            agent.onRestoreFinished();

            verify(passcodeStore).clearPasscodeWithRetry();
        }
    }

    @Test
    public void legacyBackupRulesExcludeTracksDeliveryQueue() throws Exception {
        Document rules = parse("src/main/res/xml/backup_rules.xml");

        for (String path : TRACKS_DATABASE_PATHS) {
            assertTrue(path, hasExclusion(rules.getDocumentElement(), "database", path));
        }
    }

    @Test
    public void dataExtractionRulesExcludeTracksDeliveryQueueFromBothTransports() throws Exception {
        Document rules = parse("src/main/res/xml/data_extraction_rules.xml");

        for (String transport : new String[]{"cloud-backup", "device-transfer"}) {
            Element section = (Element) rules.getElementsByTagName(transport).item(0);
            for (String path : TRACKS_DATABASE_PATHS) {
                assertTrue(transport + "/" + path, hasExclusion(section, "database", path));
            }
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

    private static boolean hasExclusion(Element section, String domain, String path) {
        NodeList excludes = section.getElementsByTagName("exclude");
        for (int index = 0; index < excludes.getLength(); index++) {
            Node exclude = excludes.item(index);
            if (exclude.getParentNode() != section) {
                continue;
            }
            Element element = (Element) exclude;
            if (domain.equals(element.getAttribute("domain")) && path.equals(element.getAttribute("path"))) {
                return true;
            }
        }
        return false;
    }
}
