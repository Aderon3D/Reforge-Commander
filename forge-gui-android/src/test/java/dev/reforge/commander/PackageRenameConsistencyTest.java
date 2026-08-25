package dev.reforge.commander;

import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Regression tests guarding the application id rename from the legacy "forge.app"
 * identifier to "dev.reforge.commander" across the Android manifest and the
 * Maven build descriptor. These are plain file/XML content checks; they require
 * no Android runtime and are safe to run in a headless JVM.
 */
public class PackageRenameConsistencyTest {

    private static final String NEW_APP_ID = "dev.reforge.commander";
    private static final String LEGACY_APP_ID = "forge.app";

    private static final File MANIFEST_FILE = new File("src/main/AndroidManifest.xml");
    private static final File POM_FILE = new File("pom.xml");

    private static Document parse(File file) throws Exception {
        assertTrue("expected file to exist: " + file.getAbsolutePath(), file.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // harden against XXE; this parses trusted local build files only
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file);
    }

    private static Element findProvider(Document manifest, String providerName) {
        NodeList providers = manifest.getElementsByTagName("provider");
        for (int i = 0; i < providers.getLength(); i++) {
            Node node = providers.item(i);
            if (node instanceof Element) {
                Element provider = (Element) node;
                if (providerName.equals(provider.getAttribute("android:name"))) {
                    return provider;
                }
            }
        }
        return null;
    }

    @Test
    public void manifestPackageIsNewAppId() throws Exception {
        Document manifest = parse(MANIFEST_FILE);
        Element root = manifest.getDocumentElement();

        String packageName = root.getAttribute("package");

        assertEquals(NEW_APP_ID, packageName);
        assertFalse("legacy app id should no longer be used as the manifest package",
                LEGACY_APP_ID.equals(packageName));
    }

    @Test
    public void publicFileProviderAuthorityMatchesNewAppId() throws Exception {
        Document manifest = parse(MANIFEST_FILE);

        Element provider = findProvider(manifest, "de.cketti.fileprovider.PublicFileProvider");
        assertNotNull("expected a de.cketti.fileprovider.PublicFileProvider <provider> entry", provider);

        String authorities = provider.getAttribute("android:authorities");
        assertEquals(NEW_APP_ID + ".publicfileprovider", authorities);
    }

    @Test
    public void publicFileProviderAuthorityIsConsistentWithManifestPackage() throws Exception {
        Document manifest = parse(MANIFEST_FILE);

        String packageName = manifest.getDocumentElement().getAttribute("package");
        Element provider = findProvider(manifest, "de.cketti.fileprovider.PublicFileProvider");
        String authorities = provider.getAttribute("android:authorities");

        // guards against the package name and the fileprovider authority drifting
        // apart in a future rename
        assertTrue(authorities + " should be scoped under package " + packageName,
                authorities.startsWith(packageName + "."));
    }

    @Test
    public void devBuildProfileUsesNewAppIdAsDevSuffix() throws Exception {
        Document pom = parse(POM_FILE);

        String devAppId = findProfileProperty(pom, "android-dev-build", "app.id.dev");

        assertEquals(NEW_APP_ID + ".dev", devAppId);
        assertFalse("legacy app id should not be used for the dev build id",
                devAppId.startsWith(LEGACY_APP_ID));
    }

    private static String findProfileProperty(Document pom, String profileId, String propertyTagName) {
        NodeList profiles = pom.getElementsByTagName("profile");
        for (int i = 0; i < profiles.getLength(); i++) {
            Element profile = (Element) profiles.item(i);
            NodeList ids = profile.getElementsByTagName("id");
            if (ids.getLength() == 0 || !profileId.equals(ids.item(0).getTextContent())) {
                continue;
            }
            NodeList props = profile.getElementsByTagName(propertyTagName);
            if (props.getLength() > 0) {
                return props.item(0).getTextContent();
            }
        }
        return null;
    }
}