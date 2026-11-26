package dev.crawlmode.engine.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactionsTest {
    private static final String GOBLINS = """
            # comment
            faction goblins floor=1 name="Marble Goblins"
            mob goblins grunt entity=minecraft:zombie name="Hall Goblin" health=14 damage=3 weight=60 mainhand=minecraft:stone_sword head=minecraft:leather_helmet
            mob goblins elite entity=minecraft:husk name="Goblin Brute" health=30 damage=6 weight=30 chest=minecraft:iron_chestplate
            mob goblins shaman entity=minecraft:witch name="Goblin Hexer" health=24 damage=2 weight=10 ability=heal_allies ability=fireball
            """;
    private static final String VALID = GOBLINS + GOBLINS.replace("goblins", "constructs") + GOBLINS.replace("goblins", "llamas");

    private static Factions load(String text) {
        return Factions.load(new StringReader(text));
    }

    private static void rejects(String text, String fragment) {
        FactionFormatException e = assertThrows(FactionFormatException.class, () -> load(text));
        assertTrue(e.getMessage().contains(fragment), e.getMessage());
    }

    @Test
    void loadsFactionsWithNamesStatsAndGear() {
        Faction f = load(VALID).faction("goblins");
        assertEquals("goblins", f.id());
        assertEquals("Marble Goblins", f.name());
        MobDef grunt = f.mob(Rank.GRUNT);
        assertEquals("Hall Goblin", grunt.name());
        assertEquals("minecraft:zombie", grunt.entity());
        assertEquals(14, grunt.health());
        assertEquals(3, grunt.damage());
        assertEquals("minecraft:stone_sword", grunt.gear().get(GearSlot.MAINHAND));
        assertEquals("minecraft:leather_helmet", grunt.gear().get(GearSlot.HEAD));
        assertEquals(List.of(Ability.HEAL_ALLIES, Ability.FIREBALL), f.mob(Rank.SHAMAN).abilities());
    }

    @Test
    void spawnRarityMustFallFromGruntToElitetoShaman() {
        rejects(VALID.replace("weight=30", "weight=60"), "rarer");
        rejects(VALID.replace("weight=10", "weight=30"), "rarer");
    }

    @Test
    void everyFloorNeedsThreeFactions() {
        rejects(GOBLINS + GOBLINS.replace("goblins", "llamas"), "at least 3");
    }

    @Test
    void shamanNeedsAnAbility() {
        rejects(VALID.replace(" ability=heal_allies ability=fireball", ""), "ability");
    }

    @Test
    void rejectsUnknownAbilityMissingRankAndBadNumbers() {
        rejects(VALID.replace("ability=fireball", "ability=explode"), "unknown ability");
        rejects(GOBLINS.lines().filter(l -> !l.contains(" elite ")).reduce("", (a, b) -> a + b + "\n"), "missing elite");
        rejects(VALID.replace("health=14", "health=0"), "health");
        rejects(VALID.replace("minecraft:zombie", "zombie"), "entity");
        rejects(VALID.replace("mob goblins grunt", "mob ghosts grunt"), "unknown faction");
        rejects(VALID + "mob goblins grunt entity=minecraft:zombie name=\"Dup\" health=5 damage=1 weight=9\n", "duplicate");
    }

    @Test
    void shippedFloorOneDataHasThreeValidFactions() throws Exception {
        try (var in = FactionsTest.class.getResourceAsStream("/data/crawlmode/faction/factions.txt")) {
            Factions all = Factions.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertTrue(all.forFloor(1).size() >= 3);
            for (Faction f : all.forFloor(1)) {
                assertFalse(f.mob(Rank.SHAMAN).abilities().isEmpty());
                assertTrue(f.mob(Rank.ELITE).health() > f.mob(Rank.GRUNT).health());
            }
        }
    }
}
