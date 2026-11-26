package dev.crawlmode.engine.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.LootTier;
import dev.crawlmode.engine.faction.Factions;
import dev.crawlmode.engine.faction.Rank;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BossesTest {
    private static Factions factions() throws Exception {
        try (var in = BossesTest.class.getResourceAsStream("/data/crawlmode/faction/factions.txt")) {
            return Factions.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static Bosses bundled() throws Exception {
        try (var in = BossesTest.class.getResourceAsStream("/data/crawlmode/faction/bosses.txt")) {
            return Bosses.load(new InputStreamReader(in, StandardCharsets.UTF_8), factions());
        }
    }

    private static final String VALID = """
            boss marble_goblins entity=minecraft:ravager name="Goblin Warlord" health=120 damage=9 tier=gold reveals=true head=minecraft:iron_helmet
            boss brass_constructs entity=minecraft:iron_golem name="The Clockwork Colossus" health=150 damage=10 tier=radiant
            boss sour_llamas entity=minecraft:vindicator name="Llama Overlord" health=110 damage=11 tier=gold
            """;

    @Test
    void bundledDataGivesEveryFloorOneFactionABoss() throws Exception {
        Bosses bosses = bundled();
        for (var f : factions().forFloor(1)) {
            BossDef def = bosses.of(f.id());
            assertTrue(def.health() > f.mob(Rank.ELITE).health(), "bosses outclass elites");
            assertTrue(def.tier() == LootTier.GOLD || def.tier() == LootTier.RADIANT, "high tier reward");
        }
    }

    @Test
    void parsesBossFields() throws Exception {
        BossDef b = Bosses.load(new StringReader(VALID), factions()).of("marble_goblins");
        assertEquals("Goblin Warlord", b.name());
        assertEquals("minecraft:ravager", b.entity());
        assertEquals(120, b.health());
        assertEquals(LootTier.GOLD, b.tier());
        assertTrue(b.revealsStairwell());
        assertEquals(1, b.gear().size());
        assertFalse(Bosses.load(new StringReader(VALID), factions()).of("sour_llamas").revealsStairwell());
    }

    @Test
    void rejectsAFactionWithoutABoss() {
        String text = VALID.lines().skip(1).reduce("", (a, b) -> a + b + "\n");
        assertThrows(BossFormatException.class, () -> Bosses.load(new StringReader(text), factions()));
    }

    @Test
    void rejectsAFloorWhereNoBossRevealsAStairwell() {
        String text = VALID.replace(" reveals=true", "");
        assertThrows(BossFormatException.class, () -> Bosses.load(new StringReader(text), factions()));
    }

    @Test
    void rejectsAFloorWhereMostBossesRevealAStairwell() {
        String text = VALID.replace("tier=radiant", "tier=radiant reveals=true");
        assertThrows(BossFormatException.class, () -> Bosses.load(new StringReader(text), factions()));
    }

    @Test
    void rejectsLowTiersAndUnknownFactions() {
        assertThrows(BossFormatException.class,
                () -> Bosses.load(new StringReader(VALID.replace("tier=radiant", "tier=copper")), factions()));
        assertThrows(BossFormatException.class,
                () -> Bosses.load(new StringReader(VALID + "boss nobody entity=minecraft:ravager name=X health=1 damage=1 tier=gold\n"),
                        factions()));
    }
}
