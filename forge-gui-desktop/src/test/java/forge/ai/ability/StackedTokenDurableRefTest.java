// REFORGE COMMANDER EXTENSION
package forge.ai.ability;

import forge.ai.AITest;
import forge.card.CardType;
import forge.card.GamePieceType;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.game.zone.PlayerZoneBattlefield;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.*;

public class StackedTokenDurableRefTest extends AITest {

    /**
     * Baseline: an unreferenced token folds via tryStackToken and is removed
     * from cardList. expandStacks() later creates a fresh Card with a new id.
     */
    @Test
    public void testUnreferencedTokenFoldsNormally() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        PlayerZoneBattlefield bf = (PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield);

        // Create a host card that generates tokens WITHOUT RememberTokens
        Card host = createTokenGenerator(game, p, false, false, false, false);
        host.setGameTimestamp(game.getNextTimestamp());
        p.getZone(ZoneType.Battlefield).add(host);

        SpellAbility sa = host.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        AbilityUtils.resolve(sa);

        // Token created without RememberTokens should fold — there should be stacks
        assertTrue("Unreferenced tokens should create stacks", bf.getStackedTokens().size() > 0);
        int stackQuantity = bf.getStackedTokens().stream().mapToInt(s -> s.getQuantity()).sum();
        assertTrue("At least one token should be stacked", stackQuantity > 0);
    }

    /**
     * Issue #57 core invariant: tokens created with RememberTokens (durable reference)
     * must stay zone-resident with their original Card identity, not be folded into stacks.
     * The TokenEffectBase selective-fold fix sets referenced=true when RememberTokens is used.
     */
    @Test
    public void testReferencedTokenStaysResident() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        PlayerZoneBattlefield bf = (PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield);

        // Create host card with RememberTokens=True in token ability
        Card host = createTokenGenerator(game, p, true, false, false, false);
        host.setGameTimestamp(game.getNextTimestamp());
        p.getZone(ZoneType.Battlefield).add(host);

        SpellAbility sa = host.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        AbilityUtils.resolve(sa);

        // Tokens created with RememberTokens should be resident (not stacked)
        assertTrue("Remembered tokens should exist on battlefield", bf.getCards().size() > 0);
        assertTrue("Host should have remembered tokens", host.getRemembered().size() > 0);

        // Verify the remembered token is reference-identical (==) to what's on battlefield
        Card rememberedToken = (Card) host.getRemembered().iterator().next();
        assertTrue("Remembered token must be on battlefield", bf.getCards().contains(rememberedToken));

        // Find the same token on battlefield and assert it's the identical object (not a copy)
        boolean foundIdenticalToken = false;
        for (Card c : bf.getCards()) {
            if (c == rememberedToken) {  // Reference equality, not .equals()
                foundIdenticalToken = true;
                break;
            }
        }
        assertTrue("Battlefield must contain the identical token object (reference equality)", foundIdenticalToken);
    }

    /**
     * Mixed scenario: tokens with RememberTokens stay resident while
     * tokens without it fold into stacks. Both coexist on the battlefield.
     */
    @Test
    public void testMixedFoldedAndResidentTokens() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        PlayerZoneBattlefield bf = (PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield);

        // First: create unreferenced tokens (should fold)
        Card gen1 = createTokenGenerator(game, p, false, false, false, false);
        gen1.setGameTimestamp(game.getNextTimestamp());
        p.getZone(ZoneType.Battlefield).add(gen1);
        SpellAbility sa1 = gen1.getFirstSpellAbility();
        sa1.setActivatingPlayer(p);
        AbilityUtils.resolve(sa1);

        int stacksAfterFirst = bf.getStackedTokens().size();
        assertTrue("Unreferenced tokens should create stacks", stacksAfterFirst > 0);

        // Second: create referenced tokens (should stay resident)
        Card gen2 = createTokenGenerator(game, p, true, false, false, false);
        gen2.setGameTimestamp(game.getNextTimestamp());
        p.getZone(ZoneType.Battlefield).add(gen2);
        SpellAbility sa2 = gen2.getFirstSpellAbility();
        sa2.setActivatingPlayer(p);
        AbilityUtils.resolve(sa2);

        assertTrue("Remembered tokens should be resident", gen2.getRemembered().size() > 0);
        Card residentToken = (Card) gen2.getRemembered().iterator().next();
        assertTrue("Resident token must be on battlefield", bf.getCards().contains(residentToken));

        // Verify both stacks and resident tokens coexist
        assertTrue("Stacks should still exist", bf.getStackedTokens().size() >= stacksAfterFirst);
        assertTrue("Resident tokens should exist", bf.getCardsUnexpanded().contains(residentToken));
    }

    /**
     * Create a minimal card with a token-generating ability.
     * This exercises the real TokenEffectBase.makeTokenTable() code path.
     */
    private Card createTokenGenerator(Game game, Player owner, boolean rememberTokens,
                                      boolean atEOT, boolean combat, boolean attach) {
        Card host = new Card(game.nextCardId(), game);
        host.setName("Token Generator");
        host.setOwner(owner);
        host.setController(owner, 0);
        host.setType(new CardType(Arrays.asList("Artifact"), false));

        // Create SpellAbility with Token effect
        SpellAbility sa = new SpellAbility.EmptySa(ApiType.Token, host, null);
        sa.setActivatingPlayer(owner);
        sa.setHostCard(host);

        // Set up parameters for token creation
        Map<String, String> params = new HashMap<>();
        params.put("TokenScript", "w_1_1_soldier");  // Standard 1/1 white soldier token
        params.put("TokenAmount", "2");  // Create 2 tokens to test folding
        params.put("TokenOwner", "You");

        if (rememberTokens) {
            params.put("RememberTokens", "True");
        }
        if (atEOT) {
            params.put("AtEOT", "Exile");
        }
        if (combat) {
            params.put("TokenAttacking", "True");
        }
        if (attach) {
            params.put("AttachAfter", "True");
            params.put("AttachedTo", "Self");
        }

        sa.setMapParams(params);
        sa.setDescription("Create tokens");
        host.addSpellAbility(sa);

        return host;
    }
}
