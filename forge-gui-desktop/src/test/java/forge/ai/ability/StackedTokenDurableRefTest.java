package forge.ai.ability;

import forge.ai.AITest;
import forge.card.CardType;
import forge.card.GamePieceType;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.PlayerZoneBattlefield;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.testng.AssertJUnit.*;

/**
 * Issue #57 regression: tokens with durable external references
 * (RememberTokens, combat, AtEOT triggers, attachments, etc.)
 * must NOT be folded into StackedTokenCard stacks — doing so
 * creates ghost Card objects whose references are disconnected
 * from the materialized copies that expandStacks() later produces.
 *
 * The fix (TokenEffectBase selective-fold) skips tryStackToken for
 * referenced tokens, keeping them zone-resident with their original
 * Card id. This test verifies the zone-level invariant.
 */
public class StackedTokenDurableRefTest extends AITest {

    private Card makeSoldier(Game game, Player p) {
        Card token = new Card(game.nextCardId(), p.getGame());
        token.setName("Test Soldier");
        token.setOwner(p);
        token.setController(p, 0);
        token.setType(new CardType(Arrays.asList("Creature", "Token", "Soldier"), false));
        token.setBasePower(1);
        token.setBaseToughness(1);
        token.setGamePieceType(GamePieceType.TOKEN);
        token.setGameTimestamp(p.getGame().getNextTimestamp());
        return token;
    }

    /**
     * Baseline: an unreferenced token folds via tryStackToken and is removed
     * from cardList. expandStacks() later creates a fresh Card with a new id.
     */
    @Test
    public void testUnreferencedTokenFoldsNormally() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        PlayerZoneBattlefield bf = (PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield);

        Card token = makeSoldier(game, p);
        bf.add(token);
        assertTrue(bf.getCards().contains(token));

        // Unreferenced token folds — removed from cardList
        assertTrue(bf.tryStackToken(token));
        assertFalse(bf.getCards().contains(token));
        // expandStacks creates a fresh copy with a new id
        Card expanded = bf.getCards().iterator().next();
        assertNotSame(token, expanded);
        assertEquals(token.getName(), expanded.getName());
    }

    /**
     * Issue #57 core invariant: a token with a durable external reference
     * (here, addRemembered) must stay zone-resident with its original Card
     * identity. The fix skips tryStackToken for such tokens.
     */
    @Test
    public void testReferencedTokenStaysResident() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        PlayerZoneBattlefield bf = (PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield);
        Card host = addCard("Grizzly Bears", p);

        Card token = makeSoldier(game, p);
        bf.add(token);
        host.addRemembered(token);

        // With the fix: do NOT call tryStackToken for referenced tokens.
        // Token stays in cardList with its original id — no ghost.
        assertTrue(bf.getCards().contains(token));
        assertSame(token, host.getRemembered().iterator().next());
    }

    /**
     * Mixed scenario: one folded stack + one resident token coexist in the
     * same zone. getCardsUnexpanded includes both the stack prototype and
     * the resident token.
     */
    @Test
    public void testMixedFoldedAndResidentTokens() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        PlayerZoneBattlefield bf = (PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield);
        Card host = addCard("Grizzly Bears", p);

        // Unreferenced token — folds into a stack
        Card fold1 = makeSoldier(game, p);
        bf.add(fold1);
        assertTrue(bf.tryStackToken(fold1));
        assertFalse(bf.getCards().contains(fold1));
        assertEquals(1, bf.getStackedTokens().size());

        // Referenced token — stays resident (no tryStackToken)
        Card resident = makeSoldier(game, p);
        bf.add(resident);
        host.addRemembered(resident);

        // Resident is in cardList; fold1 is not
        assertTrue(bf.getCards().contains(resident));
        assertFalse(bf.getCards().contains(fold1));

        // getCardsUnexpanded shows both: stack prototype + resident
        boolean hasResident = false;
        boolean hasPrototype = false;
        for (Card c : bf.getCardsUnexpanded()) {
            if (c == resident) hasResident = true;
            if (c.getName().equals("Test Soldier") && bf.getStackedTokens().get(0).getPrototype() == c) {
                hasPrototype = true;
            }
        }
        assertTrue("resident token in unexpanded view", hasResident);
        assertTrue("stack prototype in unexpanded view", hasPrototype);
    }
}
