package forge.game;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads Scryfall oracle tags from a local text index and provides fast lookup.
 * Tags are community-maintained labels describing card roles, synergy, and archetype.
 * Built from Scryfall bulk data (oracle-tags + oracle-cards JSONL files).
 *
 * File format: one line per card, "CardName|tag1,tag2,tag3"
 *
 * REFORGE COMMANDER EXTENSION
 */
public class CardTagIndex {
    private static CardTagIndex instance;

    // ponytail: volatile + lazy init, safe read after init
    // Known limitation: concurrent load() calls could race; getInstance() returns last-wins.
    // Upgrade path: use AtomicReference<Map> + compareAndSet for lock-free single-load guarantee,
    // or require explicit init before multi-threaded access (e.g., in game startup).
    private volatile Map<String, Set<String>> tags = Collections.emptyMap();

    // --- AI-relevant tag constants ---

    // Threat assessment
    public static final String TAG_SWEEPER = "sweeper";
    public static final String TAG_DRAW_ENGINE = "draw-engine";
    public static final String TAG_PURE_DRAW = "pure-draw";
    public static final String TAG_SPOT_REMOVAL = "spot-removal";
    public static final String TAG_COUNTERSPELL = "counterspell";
    public static final String TAG_HATEBEAR = "hatebear";
    public static final String TAG_MASS_LAND_DENIAL = "mass-land-denial";
    public static final String TAG_RAMP = "ramp";
    public static final String TAG_MANA_DORK = "mana-dork";
    public static final String TAG_MANA_ROCK = "mana-rock";
    public static final String TAG_ANTHEM = "anthem";
    public static final String TAG_KEYWORD_ANTHEM = "keyword-anthem";
    public static final String TAG_EVASION = "evasion";

    // Sacrifice willingness
    public static final String TAG_SYNERGY_SACRIFICE_SELF = "synergy-sacrifice-self";
    public static final String TAG_SACRIFICE_OUTLET = "sacrifice-outlet-creature";

    // Combo / synergy
    public static final String TAG_EQUIPMENT = "synergy-equipment";
    public static final String TAG_AURA = "synergy-aura";
    public static final String TAG_TOKEN = "synergy-token";
    public static final String TAG_GRAVEYARD = "synergy-graveyard-cast";
    public static final String TAG_MILL = "synergy-mill";
    public static final String TAG_LIFELINK = "synergy-lifelink";

    // Hate tags
    public static final String TAG_HATE_ARTIFACT = "hate-artifact";
    public static final String TAG_HATE_ENCHANTMENT = "hate-enchantment";
    public static final String TAG_HATE_GRAVEYARD = "hate-graveyard";
    public static final String TAG_HATE_FLYING = "hate-flying";

    // Tutor tags
    public static final String TAG_TUTOR_CARD = "tutor-card";
    public static final String TAG_TUTOR_CREATURE = "tutor-creature";

    private static final Set<String> HIGH_THREAT_TAGS = Set.of(
        TAG_SWEEPER, TAG_DRAW_ENGINE, TAG_PURE_DRAW,
        TAG_COUNTERSPELL, TAG_HATEBEAR, TAG_MASS_LAND_DENIAL
    );

    private static final Set<String> SACRIFICE_WORTHY_TAGS = Set.of(
        TAG_SYNERGY_SACRIFICE_SELF, "death-trigger", "leaves-creature"
    );

    private static final CardTagIndex EMPTY = new CardTagIndex();

    public static synchronized CardTagIndex getInstance() {
        return instance != null ? instance : EMPTY;
    }

    public static synchronized void load(String txtPath) {
        instance = new CardTagIndex();
        instance.loadFromFile(txtPath);
    }

    private void loadFromFile(String path) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            Map<String, Set<String>> result = new HashMap<>(36000);
            String line;
            while ((line = reader.readLine()) != null) {
                int pipe = line.indexOf('|');
                if (pipe < 0) continue;
                String cardName = line.substring(0, pipe);
                String tagStr = line.substring(pipe + 1);
                if (tagStr.isEmpty()) continue;
                Set<String> tagSet = new HashSet<>();
                for (String tag : tagStr.split(",", -1)) {
                    if (!tag.isEmpty()) tagSet.add(tag);
                }
                result.put(cardName, Collections.unmodifiableSet(tagSet));
            }
            this.tags = Collections.unmodifiableMap(result);
            System.out.println("CardTagIndex: loaded " + tags.size() + " cards with tags from " + path);
        } catch (IOException e) {
            System.err.println("CardTagIndex: failed to load " + path + " — " + e.getMessage());
        }
    }

    public Set<String> getTags(String cardName) {
        return tags.getOrDefault(cardName, Collections.emptySet());
    }

    public boolean hasTag(String cardName, String tag) {
        return tags.getOrDefault(cardName, Collections.emptySet()).contains(tag);
    }

    public boolean hasAnyTag(String cardName, Set<String> tagSet) {
        Set<String> cardTags = tags.getOrDefault(cardName, Collections.emptySet());
        for (String tag : tagSet) {
            if (cardTags.contains(tag)) return true;
        }
        return false;
    }

    // threat multiplier based on Scryfall tags
    // returns 1.0 for no adjustment, >1.0 for high-threat targets
    public float getThreatMultiplier(String cardName) {
        Set<String> cardTags = tags.getOrDefault(cardName, Collections.emptySet());
        if (cardTags.isEmpty()) return 1.0f;

        float multiplier = 1.0f;
        for (String tag : cardTags) {
            if (HIGH_THREAT_TAGS.contains(tag)) {
                multiplier += 0.25f;
            }
        }
        return Math.min(multiplier, 2.5f);
    }

    // sacrifice willingness boost based on tags
    // returns 0 if not a sacrifice target, 1-6 (SacMe scale) if it is
    public int getSacMeBoost(String cardName) {
        Set<String> cardTags = tags.getOrDefault(cardName, Collections.emptySet());
        if (cardTags.isEmpty()) return 0;

        int boost = 0;
        for (String tag : cardTags) {
            if (SACRIFICE_WORTHY_TAGS.contains(tag)) {
                boost = Math.max(boost, 3);
            }
            if (tag.equals(TAG_SYNERGY_SACRIFICE_SELF)) {
                boost = Math.max(boost, 5);
            }
        }
        return boost;
    }

    // archetype classification for deck-level play pattern tuning
    public DeckArchetype classifyDeckArchetype(Map<String, Integer> cardCounts) {
        int aggroScore = 0, controlScore = 0, comboScore = 0, total = 0;

        for (Map.Entry<String, Integer> entry : cardCounts.entrySet()) {
            String cardName = entry.getKey();
            int count = entry.getValue();
            Set<String> cardTags = tags.getOrDefault(cardName, Collections.emptySet());

            total += count;
            for (String tag : cardTags) {
                if (tag.equals("evasion") || tag.equals("attacking-matters") || tag.equals("haste")) {
                    aggroScore += count;
                }
                if (HIGH_THREAT_TAGS.contains(tag) || tag.equals("counterspell-reusable") || tag.equals("lockdown-creature")) {
                    controlScore += count;
                }
                if (tag.equals("sacrifice-outlet-creature") || tag.equals("synergy-graveyard-cast")) {
                    comboScore += count;
                }
            }
        }

        if (total == 0) return DeckArchetype.UNKNOWN;
        float threshold = total * 0.15f;
        if (aggroScore > threshold && aggroScore > controlScore && aggroScore > comboScore) return DeckArchetype.AGGRO;
        if (controlScore > threshold && controlScore > comboScore) return DeckArchetype.CONTROL;
        if (comboScore > threshold) return DeckArchetype.COMBO;
        return DeckArchetype.MIDRANGE;
    }

    public enum DeckArchetype {
        AGGRO, CONTROL, COMBO, MIDRANGE, UNKNOWN
    }

    public int size() {
        return tags.size();
    }
}
