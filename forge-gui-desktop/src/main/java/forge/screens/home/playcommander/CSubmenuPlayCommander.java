/*
 * REFORGE COMMANDER EXTENSION
 *
 * Controller for the Commander play submenu.
 */
package forge.screens.home.playcommander;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import forge.gui.framework.ICDoc;
import forge.gui.reforge.ReforgeMatchLayoutPresets;
import forge.menus.IMenuProvider;
import forge.menus.MenuUtil;
import forge.screens.home.CLobby;
import forge.util.gui.SOptionPane;

public enum CSubmenuPlayCommander implements ICDoc, IMenuProvider {

    SINGLETON_INSTANCE;

    private final VSubmenuPlayCommander view = VSubmenuPlayCommander.SINGLETON_INSTANCE;
    private final CLobby lobby = view.getLobby().getController();

    @Override
    public void register() {
    }

    @Override
    public void update() {
        MenuUtil.setMenuProvider(this);
        lobby.update();
    }

    @Override
    public void initialize() {
        lobby.initialize();
    }

    /**
     * Creates the Commander submenu for selecting or restoring battlefield layouts.
     *
     * @return the available Commander menus
     */
    @Override
    public List<JMenu> getMenus() {
        final List<JMenu> menus = new ArrayList<>();
        final JMenu layoutMenu = new JMenu("Battlefield Layout");
        for (int n = 2; n <= ReforgeMatchLayoutPresets.MAX_PLAYERS; n++) {
            final int players = n;
            layoutMenu.add(item(players + " Players", e -> applyPreset(players)));
        }
        layoutMenu.addSeparator();
        layoutMenu.add(item("Restore Default Layout", e -> restoreDefault()));
        menus.add(layoutMenu);
        return menus;
    }

    /**
     * Creates a menu item with the specified label and action listener.
     *
     * @param text   the menu item's label
     * @param action the listener invoked when the item is selected
     * @return       the configured menu item
     */
    private static JMenuItem item(final String text, final ActionListener action) {
        final JMenuItem mi = new JMenuItem(text);
        mi.addActionListener(action);
        return mi;
    }

    /**
     * Applies the canonical battlefield layout for the specified player count.
     *
     * @param players the number of players supported by the layout preset
     */
    private static void applyPreset(final int players) {
        try {
            ReforgeMatchLayoutPresets.apply(players);
            SOptionPane.showMessageDialog("Canonical " + players + "-player battlefield layout applied.\n"
                    + "It takes effect when the next match starts.",
                    "Battlefield Layout");
        } catch (final IOException ex) {
            SOptionPane.showErrorDialog(ex.getLocalizedMessage(), "Battlefield Layout");
        }
    }

    /**
     * Restores the stock two-player battlefield layout and displays the operation result.
     */
    private static void restoreDefault() {
        try {
            ReforgeMatchLayoutPresets.restoreDefault();
            SOptionPane.showMessageDialog("Stock 2-player battlefield layout restored.",
                    "Battlefield Layout");
        } catch (final IOException ex) {
            SOptionPane.showErrorDialog(ex.getLocalizedMessage(), "Battlefield Layout");
        }
    }
}
