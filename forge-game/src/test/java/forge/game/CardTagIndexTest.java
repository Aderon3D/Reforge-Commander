package forge.game;

import org.testng.annotations.Test;
import org.testng.AssertJUnit;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class CardTagIndexTest {

    private CardTagIndex createIndex(String... lines) throws IOException {
        File tmp = File.createTempFile("card-tags-", ".txt");
        tmp.deleteOnExit();
        try (FileWriter w = new FileWriter(tmp)) {
            for (String line : lines) {
                w.write(line);
                w.write("\n");
            }
        }
        CardTagIndex.load(tmp.getAbsolutePath());
        return CardTagIndex.getInstance();
    }

    @Test
    public void testLoadAndLookup() throws IOException {
        CardTagIndex idx = createIndex(
            "Lightning Bolt|spot-removal,evasion",
            "Sol Ring|mana-rock,ramp"
        );
        AssertJUnit.assertEquals(2, idx.size());
        AssertJUnit.assertTrue(idx.hasTag("Lightning Bolt", "spot-removal"));
        AssertJUnit.assertTrue(idx.hasTag("Sol Ring", "mana-rock"));
        AssertJUnit.assertFalse(idx.hasTag("Lightning Bolt", "mana-rock"));
    }

    @Test
    public void testGetTags() throws IOException {
        CardTagIndex idx = createIndex("Counterspell|counterspell,draw-engine");
        Set<String> tags = idx.getTags("Counterspell");
        AssertJUnit.assertEquals(2, tags.size());
        AssertJUnit.assertTrue(tags.contains("counterspell"));
        AssertJUnit.assertTrue(tags.contains("draw-engine"));
    }

    @Test
    public void testGetTagsUnknownCard() throws IOException {
        CardTagIndex idx = createIndex("Bolt|evasion");
        Set<String> tags = idx.getTags("Unknown Card");
        AssertJUnit.assertTrue(tags.isEmpty());
    }

    @Test
    public void testHasAnyTag() throws IOException {
        CardTagIndex idx = createIndex("Card1|sweeper,draw-engine");
        AssertJUnit.assertTrue(idx.hasAnyTag("Card1", Set.of("sweeper", "counterspell")));
        AssertJUnit.assertFalse(idx.hasAnyTag("Card1", Set.of("counterspell", "ramp")));
    }

    @Test
    public void testThreatMultiplier() throws IOException {
        CardTagIndex idx = createIndex(
            "Wrath of God|sweeper",
            "Brainstorm|draw-engine",
            "Sol Ring|mana-rock"
        );
        // Single threat tag: 1.0 + 0.25 = 1.25
        AssertJUnit.assertEquals(1.25f, idx.getThreatMultiplier("Wrath of God"), 0.01f);
        // Single threat tag (draw-engine): 1.0 + 0.25 = 1.25
        AssertJUnit.assertEquals(1.25f, idx.getThreatMultiplier("Brainstorm"), 0.01f);
        // No threat tags
        AssertJUnit.assertEquals(1.0f, idx.getThreatMultiplier("Sol Ring"), 0.01f);
        // Unknown card
        AssertJUnit.assertEquals(1.0f, idx.getThreatMultiplier("Unknown"), 0.01f);
    }

    @Test
    public void testThreatMultiplierCapped() throws IOException {
        // Cards with many threat tags should cap at 2.5
        CardTagIndex idx = createIndex("Overloaded|sweeper,draw-engine,pure-draw,counterspell,hatebear,mass-land-denial");
        AssertJUnit.assertEquals(2.5f, idx.getThreatMultiplier("Overloaded"), 0.01f);
    }

    @Test
    public void testSacMeBoost() throws IOException {
        CardTagIndex idx = createIndex(
            "Bloodghast|synergy-sacrifice-self,death-trigger",
            "Doom Foretold|sacrifice-outlet-creature",
            "Sol Ring|mana-rock"
        );
        // synergy-sacrifice-self: boost = 5
        AssertJUnit.assertEquals(5, idx.getSacMeBoost("Bloodghast"));
        // sacrifice-outlet-creature is NOT in SACRIFICE_WORTHY_TAGS: boost = 0
        AssertJUnit.assertEquals(0, idx.getSacMeBoost("Doom Foretold"));
        // No sacrifice tags
        AssertJUnit.assertEquals(0, idx.getSacMeBoost("Sol Ring"));
    }

    @Test
    public void testArchetypeClassification() throws IOException {
        CardTagIndex idx = createIndex(
            "Goblin Guide|evasion,haste,attacking-matters",
            "Lightning Bolt|spot-removal,evasion",
            "Counterspell|counterspell",
            "Sol Ring|mana-rock"
        );
        // Aggro: 2 creatures with evasion/haste/attacking-matters tags
        Map<String, Integer> aggroDeck = Map.of("Goblin Guide", 4, "Lightning Bolt", 4);
        AssertJUnit.assertEquals(CardTagIndex.DeckArchetype.AGGRO, idx.classifyDeckArchetype(aggroDeck));

        // Control: 2 counterspell creatures
        Map<String, Integer> controlDeck = Map.of("Counterspell", 4, "Sol Ring", 4);
        AssertJUnit.assertEquals(CardTagIndex.DeckArchetype.CONTROL, idx.classifyDeckArchetype(controlDeck));

        // Empty deck
        AssertJUnit.assertEquals(CardTagIndex.DeckArchetype.UNKNOWN, idx.classifyDeckArchetype(Map.of()));
    }

    @Test
    public void testEmptyIndexReturnsDefaults() throws IOException {
        // Load a minimal index, then test defaults for unknown cards
        CardTagIndex idx = createIndex("Bolt|evasion");
        AssertJUnit.assertEquals(1, idx.size());
        AssertJUnit.assertTrue(idx.getTags("Unknown Card").isEmpty());
        AssertJUnit.assertFalse(idx.hasTag("Unknown Card", "anything"));
        AssertJUnit.assertEquals(1.0f, idx.getThreatMultiplier("Unknown Card"), 0.01f);
        AssertJUnit.assertEquals(0, idx.getSacMeBoost("Unknown Card"));
    }
}
