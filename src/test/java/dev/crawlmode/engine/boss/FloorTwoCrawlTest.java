package dev.crawlmode.engine.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.LootSource;
import dev.crawlmode.engine.faction.Faction;
import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.faction.Factions;
import dev.crawlmode.engine.faction.Rank;
import dev.crawlmode.engine.faction.SpawnPlanner;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.FloorSpec;
import dev.crawlmode.engine.floorgen.Gate;
import dev.crawlmode.engine.floorgen.TemplateLibrary;
import java.io.StringReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorTwoCrawlTest {
    private static final Factions FACTIONS = Factions.load(new StringReader(FloorSpec.readFactionData("factions")));
    private static final Bosses BOSSES = Bosses.load(new StringReader(FloorSpec.readFactionData("bosses")), FACTIONS);
    private static final FloorSpec SPEC = FloorSpec.forFloor(2).orElseThrow();
    private static final TemplateLibrary LIBRARY = library();

    private static TemplateLibrary library() {
        try (var in = FloorTwoCrawlTest.class.getResourceAsStream(SPEC.templatesResource())) {
            return TemplateLibrary.parse(new String(in.readAllBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void floorTwoHasGrubDingoAndWaspFactionsWithEveryRankAndABoss() {
        List<Faction> two = FACTIONS.forFloor(2);
        assertEquals(List.of("mash_grubs", "dune_dingoes", "rust_wasps"), two.stream().map(Faction::id).toList());
        for (Faction f : two) {
            for (Rank rank : List.of(Rank.GRUNT, Rank.ELITE, Rank.SHAMAN)) {
                assertNotNull(f.mob(rank), f.id() + " " + rank);
            }
            assertTrue(f.mob(Rank.GRUNT).weight() > f.mob(Rank.ELITE).weight());
            assertTrue(f.mob(Rank.ELITE).weight() > f.mob(Rank.SHAMAN).weight());
            assertTrue(BOSSES.of(f.id()).health() > f.mob(Rank.ELITE).health());
        }
        assertEquals(1, two.stream().filter(f -> BOSSES.of(f.id()).revealsStairwell()).count());
    }

    @Test
    void floorOneDataIsUntouchedByTheFloorTwoFiles() {
        assertEquals(3, FACTIONS.forFloor(1).size());
    }

    @Test
    void floorTwoBossesSitGatedBehindTheirFactionsAndEveryAreaHasAFaction() {
        for (long seed = 0; seed < 40; seed++) {
            FloorLayout layout = FloorLayout.generate(seed, LIBRARY, SPEC);
            FactionAreas areas = FactionAreas.assign(FACTIONS.forFloor(2), layout.areaCount());
            BossPlan plan = BossPlan.generate(layout, areas, FACTIONS.forFloor(2), BOSSES, seed);
            assertEquals(3, plan.placements().size());
            BossEncounter encounter = new BossEncounter(layout, areas, plan);
            for (Gate g : layout.gates()) {
                assertTrue(encounter.isGateClosed(g));
            }
            BossPlacement first = plan.placements().get(0);
            var kill = encounter.onBossKilled(first.def().factionId(), UUID.randomUUID()).orElseThrow();
            assertEquals(LootSource.BOSS_KILL, kill.loot().source());
        }
    }

    @Test
    void floorTwoMobsSpawnOnlyOutsideTheStartAreaAndBelongToFloorTwoFactions() {
        FloorLayout layout = FloorLayout.generate(3, LIBRARY, SPEC);
        FactionAreas areas = FactionAreas.assign(FACTIONS.forFloor(2), layout.areaCount());
        boolean any = false;
        for (var cell : layout.cells()) {
            var spawns = SpawnPlanner.forCell(cell, areas, 3);
            if (cell.area() <= 0) {
                assertTrue(spawns.isEmpty());
            }
            for (var s : spawns) {
                any = true;
                assertTrue(FACTIONS.forFloor(2).stream().anyMatch(f -> f.id().equals(s.mob().factionId())));
            }
        }
        assertTrue(any);
        assertFalse(layout.stairwells().isEmpty());
    }
}
