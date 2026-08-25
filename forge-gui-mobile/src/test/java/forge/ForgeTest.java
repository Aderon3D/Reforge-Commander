package forge;

import com.badlogic.gdx.utils.Clipboard;
import forge.gui.GuiBase;
import forge.interfaces.IDeviceAdapter;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertSame;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Tests for {@link Forge#getApp}, specifically the app-specific-directory detection
 * logic that was updated to key off the "dev.reforge.commander" package/app id
 * instead of the legacy "forge.app" identifier.
 */
public class ForgeTest {

    private static final Clipboard FAKE_CLIPBOARD = new Clipboard() {
        @Override
        public boolean hasContents() {
            return false;
        }

        @Override
        public String getContents() {
            return "";
        }

        @Override
        public void setContents(String contents) {
            // no-op
        }
    };

    private static final IDeviceAdapter FAKE_DEVICE_ADAPTER = new FakeDeviceAdapter();

    @BeforeMethod
    @AfterMethod
    public void resetForgeStaticState() throws Exception {
        setStaticField("app", null);
        setStaticField("clipboard", null);
        setStaticField("deviceAdapter", null);
        Forge.isPortraitMode = false;
        Forge.isTabletDevice = false;
        Forge.androidVersion = 0;

        GuiBase.setInterface(null);
        GuiBase.setUsingAppDirectory(false);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = Forge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    @DataProvider(name = "newPackageAssetDirs")
    public Object[][] newPackageAssetDirs() {
        return new Object[][] {
                {"/storage/emulated/0/Android/obb/dev.reforge.commander/Forge/"},
                {"dev.reforge.commander"},
                {"/data/data/dev.reforge.commander/files/"},
        };
    }

    @DataProvider(name = "nonMatchingAssetDirs")
    public Object[][] nonMatchingAssetDirs() {
        return new Object[][] {
                {"/storage/emulated/0/Forge/"},
                // legacy package id must no longer trigger app-directory mode
                {"/storage/emulated/0/Android/obb/forge.app/Forge/"},
                {""},
                // String#contains is case-sensitive; differently-cased id should not match
                {"/data/data/DEV.REFORGE.COMMANDER/files/"},
        };
    }

    @Test(dataProvider = "newPackageAssetDirs")
    public void getApp_marksUsingAppDirectory_whenAssetDirContainsNewPackageId(String assetDir) {
        Forge.getApp(null, FAKE_CLIPBOARD, FAKE_DEVICE_ADAPTER, assetDir, false, false, 30);

        assertTrue("assetDir '" + assetDir + "' should trigger app-specific directory mode",
                GuiBase.isUsingAppDirectory());
    }

    @Test(dataProvider = "nonMatchingAssetDirs")
    public void getApp_doesNotMarkUsingAppDirectory_whenAssetDirLacksNewPackageId(String assetDir) {
        Forge.getApp(null, FAKE_CLIPBOARD, FAKE_DEVICE_ADAPTER, assetDir, false, false, 30);

        assertFalse("assetDir '" + assetDir + "' should not trigger app-specific directory mode",
                GuiBase.isUsingAppDirectory());
    }

    @Test
    public void getApp_returnsNonNullApplicationListener() {
        Object result = Forge.getApp(null, FAKE_CLIPBOARD, FAKE_DEVICE_ADAPTER,
                "/data/data/dev.reforge.commander/files/", false, false, 30);

        assertNotNull(result);
    }

    @Test
    public void getApp_setsPortraitTabletAndApiFieldsOnFirstCall() {
        Forge.getApp(null, FAKE_CLIPBOARD, FAKE_DEVICE_ADAPTER,
                "/data/data/dev.reforge.commander/files/", true, true, 33);

        assertTrue(Forge.isPortraitMode);
        assertTrue(Forge.isTabletDevice);
        assertEquals(33, Forge.androidVersion);
    }

    @Test
    public void getApp_isIdempotent_secondCallReturnsSameCachedInstance() {
        Object first = Forge.getApp(null, FAKE_CLIPBOARD, FAKE_DEVICE_ADAPTER,
                "/data/data/dev.reforge.commander/files/", false, false, 30);

        Object second = Forge.getApp(null, FAKE_CLIPBOARD, FAKE_DEVICE_ADAPTER,
                "/data/data/forge.app/files/", true, true, 99);

        assertSame(first, second);
        // the second (non-matching, and differently configured) call must be ignored
        // entirely since app initialization only happens once
        assertTrue(GuiBase.isUsingAppDirectory());
        assertFalse(Forge.isPortraitMode);
        assertFalse(Forge.isTabletDevice);
        assertEquals(30, Forge.androidVersion);
    }

    @Test
    public void getApp_doesNotReinitializeGuiBase_whenInterfaceAlreadySet() {
        // use the real GuiMobile implementation to simulate an already-configured
        // GuiBase interface (construction is side-effect free: no LibGDX backend needed)
        GuiBase.setInterface(new GuiMobile("/data/data/dev.reforge.commander/files/"));
        GuiBase.setUsingAppDirectory(true);

        Forge.getApp(null, FAKE_CLIPBOARD, FAKE_DEVICE_ADAPTER,
                "/data/data/some/other/dir/", false, false, 30);

        // since GuiBase already had an interface configured, getApp should not
        // touch the app-directory flag at all
        assertTrue(GuiBase.isUsingAppDirectory());
    }

    private static final class FakeDeviceAdapter implements IDeviceAdapter {
        @Override
        public boolean isConnectedToInternet() {
            return false;
        }

        @Override
        public boolean isConnectedToWifi() {
            return false;
        }

        @Override
        public boolean isTablet() {
            return false;
        }

        @Override
        public String getDownloadsDir() {
            return "/tmp/downloads";
        }

        @Override
        public String getVersionString() {
            return "TEST-VERSION";
        }

        @Override
        public String getLatestChanges(String commitsAtom, Date buildDateOriginal, Date maxDate) {
            return "";
        }

        @Override
        public String getReleaseTag(String releaseAtom) {
            return "";
        }

        @Override
        public boolean openFile(String filename) {
            return false;
        }

        @Override
        public void setLandscapeMode(boolean landscapeMode) {
            // no-op
        }

        @Override
        public void preventSystemSleep(boolean preventSleep) {
            // no-op
        }

        @Override
        public void restart() {
            // no-op
        }

        @Override
        public void exit() {
            // no-op
        }

        @Override
        public void closeSplashScreen() {
            // no-op
        }

        @Override
        public void convertToJPEG(InputStream input, OutputStream output) throws IOException {
            // no-op
        }

        @Override
        public void convertToPNG(InputStream input, OutputStream output) throws IOException {
            // no-op
        }

        @Override
        public Pair<Integer, Integer> getRealScreenSize(boolean real) {
            return Pair.of(0, 0);
        }

        @Override
        public ArrayList<String> getGamepads() {
            return new ArrayList<>();
        }

        @Override
        public org.jupnp.UpnpServiceConfiguration getUpnpPlatformService() {
            return null;
        }

        @Override
        public boolean needFileAccess() {
            return false;
        }

        @Override
        public void requestFileAcces() {
            // no-op
        }
    }
}