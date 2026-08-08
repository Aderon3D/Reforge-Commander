package forge.game;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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
 */
public class CardTagIndex {
    private static CardTagIndex instance;

    // ponytail: volatile + lazy init, safe read after init
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

    /**
     * Retrieves the currently loaded card tag index.
     *
     * @return the loaded index, or an empty index if none has been loaded
     */
    public static synchronized CardTagIndex getInstance() {
        return instance != null ? instance : EMPTY;
    }

    /**
     * Loads card tags from the specified file and replaces the current index.
     *
     * @param txtPath path to the UTF-8 card tag file
     */
    public static synchronized void load(String txtPath) {
        instance = new CardTagIndex();
        instance.loadFromFile(txtPath);
    }

    /**
     * Loads card tags from a UTF-8 file formatted as {@code CardName|tag1,tag2}.
     *
     * <p>Malformed records and records without tags are skipped. Successfully loaded
     * card and tag mappings are stored as unmodifiable collections. I/O failures
     * leave the current index unchanged.
     *
     * @param path path to the tag data file
     */
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

    /**
     * Retrieves the Scryfall tags associated with a card name.
     *
     * @param cardName the card name to look up
     * @return the card's tags, or an empty set if the card is not indexed
     */
    public Set<String> getTags(String cardName) {
        return tags.getOrDefault(cardName, Collections.emptySet());
    }

    /**
     * Determines whether a card has the specified tag.
     *
     * @param cardName the card name
     * @param tag      the tag to check
     * @return {@code true} if the card has the tag, {@code false} otherwise
     */
    public boolean hasTag(String cardName, String tag) {
        return tags.getOrDefault(cardName, Collections.emptySet()).contains(tag);
    }

    /**
     * Determines whether a card has at least one tag from the supplied set.
     *
     * @param cardName the card name to check
     * @param tagSet   the tags to search for
     * @return         {@code true} if the card has at least one supplied tag, {@code false} otherwise
     */
    public boolean hasAnyTag(String cardName, Set<String> tagSet) {
        Set<String> cardTags = tags.getOrDefault(cardName, Collections.emptySet());
        for (String tag : tagSet) {
            if (cardTags.contains(tag)) return true;
        }
        return false;
    }

    // ponytail: threat multiplier based on Scryfall tags
    /**
     * Calculates the threat multiplier for a card based on its high-threat tags.
     *
     * @param cardName the card name to evaluate
     * @return the threat multiplier, from {@code 1.0} to {@code 2.5}
     */
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

    // ponytail: sacrifice willingness boost based on tags
    /**
     * Determines the sacrifice value assigned to a card based on its tags.
     *
     * @param cardName the card name to evaluate
     * @return the sacrifice boost: {@code 0} for cards without relevant tags, {@code 3} for sacrifice-worthy cards, or at least {@code 5} for self-sacrifice synergy cards
     */
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

    /**
     * Classifies a deck according to the prevalence of aggro, control, and combo card tags.
     *
     * @param cardCounts the card names and quantities used to evaluate the deck
     * @return the matching archetype, or {@link DeckArchetype#UNKNOWN} when the deck has no cards
     */
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

    /**
     * Gets the number of cards in the index.
     *
     * @return the number of indexed cards
     */
    public int size() {
        return tags.size();
    }
}
