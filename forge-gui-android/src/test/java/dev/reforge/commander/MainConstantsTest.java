package dev.reforge.commander;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Regression tests for the "dev.reforge.commander" application-id rename in
 * {@code Main.java}. Rather than loading the {@code Main} class itself (which
 * extends {@code AndroidApplication} and is only meant to run inside a real
 * Android/Robolectric environment), these tests inspect the source file
 * directly so they can run in a plain headless JVM.
 */
public class MainConstantsTest {

    private static final Path MAIN_JAVA = Path.of("src", "dev", "reforge", "commander", "Main.java");

    private static String readMainSource() throws IOException {
        assertTrue("expected to find " + MAIN_JAVA.toAbsolutePath(), Files.isRegularFile(MAIN_JAVA));
        return Files.readString(MAIN_JAVA, StandardCharsets.UTF_8);
    }

    @Test
    public void resPkgFallbackConstantUsesNewAppId() throws IOException {
        String source = readMainSource();

        Pattern pattern = Pattern.compile(
                "RES_PKG_FALLBACK\\s*=\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(source);

        assertTrue("expected to find RES_PKG_FALLBACK constant declaration", matcher.find());
        assertEquals("dev.reforge.commander", matcher.group(1));
    }

    @Test
    public void openFileUsesNewPublicFileProviderAuthority() throws IOException {
        String source = readMainSource();

        Pattern pattern = Pattern.compile(
                "PublicFileProvider\\.getUriForFile\\([^,]+,\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(source);

        assertTrue("expected to find PublicFileProvider.getUriForFile(...) call", matcher.find());
        assertEquals("dev.reforge.commander.publicfileprovider", matcher.group(1));
    }

    @Test
    public void sourceNoLongerReferencesLegacyAppId() throws IOException {
        String source = readMainSource();

        assertFalse("legacy \"forge.app\" identifier should have been fully replaced",
                source.contains("\"forge.app\""));
        assertFalse("legacy fileprovider authority should have been fully replaced",
                source.contains("com.mydomain.publicfileprovider"));
    }
}