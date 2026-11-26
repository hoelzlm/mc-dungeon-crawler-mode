package dev.crawlmode.engine.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.LootSource;
import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.faction.Factions;
import dev.crawlmode.engine.floorgen.Cell;
import dev.crawlmode.engine.floorgen.CellPos;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.FloorTemplate;
import dev.crawlmode.engine.floorgen.Gate;
import dev.crawlmode.engine.floorgen.Stairwell;
import dev.crawlmode.engine.floorgen.TemplateLibrary;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BossEncounterTest {
    private static final UUID CRAWLER = UUID.randomUUID();
    private static final Factions FACTIONS = factions();
    private static final Bosses BOSSES = bosses();
    private static final TemplateLibrary LIBRARY = library();

    private static Factions factions() {
        try (var in = BossEncounterTest.class.getResourceAsStream("/data/crawlmode/faction/factions.txt")) {
            return Factions.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Bosses bosses() {
        try (var in = BossEncounterTest.class.getResourceAsStream("/data/crawlmode/faction/bosses.txt")) {
            return Bosses.load(new InputStreamReader(in, StandardCharsets.UTF_8), FACTIONS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static TemplateLibrary library() {
        try (var in = BossEncounterTest.class.getResourceAsStream("/data/crawlmode/floor/floor1_templates.txt")) {
            return TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record Setup(FloorLayout layout, FactionAreas areas, BossPlan plan) {}

    private static Setup setup(long seed) {
        FloorLayout layout = FloorLayout.generate(seed, LIBRARY);
        FactionAreas areas = FactionAreas.assign(FACTIONS.forFloor(1), layout.areaCount());
        return new Setup(layout, areas, BossPlan.generate(layout, areas, FACTIONS.forFloor(1), BOSSES, seed));
    }

    @Test
    void everyFactionGetsOneBossInARoomOfItsOwnArea() {
        for (long seed = 0; seed < 60; seed++) {
            Setup s = setup(seed);
            assertEquals(FACTIONS.forFloor(1).size(), s.plan().placements().size());
            for (BossPlacement p : s.plan().placements()) {
                assertEquals(p.def().factionId(), s.areas().factionOf(p.area()).orElseThrow().id());
                Cell cell = s.layout().cell(new CellPos(
                        Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16))).orElseThrow();
                assertEquals(p.area(), cell.area());
                assertEquals(FloorTemplate.Role.ROOM, cell.template().role(), "boss stands in an ordinary room");
            }
        }
    }

    @Test
    void placementsInACellAreExactlyTheBossesStandingThere() {
        Setup s = setup(7);
        int found = 0;
        for (Cell cell : s.layout().cells()) {
            for (BossPlacement p : s.plan().placementsIn(cell.pos())) {
                assertEquals(cell.pos().x(), Math.floorDiv(p.x(), 16));
                assertEquals(cell.pos().z(), Math.floorDiv(p.z(), 16));
                found++;
            }
        }
        assertEquals(s.plan().placements().size(), found, "every Boss is in exactly one cell");
        BossPlacement first = s.plan().placements().get(0);
        assertTrue(s.plan().placementsIn(new CellPos(
                Math.floorDiv(first.x(), 16), Math.floorDiv(first.z(), 16))).contains(first));
        assertTrue(s.plan().placementsIn(new CellPos(1000, 1000)).isEmpty());
    }

    @Test
    void placementIsRandomPerGeneration() {
        Set<List<Integer>> spots = new HashSet<>();
        for (long seed = 0; seed < 30; seed++) {
            BossPlacement p = setup(seed).plan().placements().get(0);
            spots.add(List.of(p.x(), p.z()));
        }
        assertTrue(spots.size() > 5, "boss spot varies across generations");
    }

    @Test
    void placementIsDeterministicForASeed() {
        assertEquals(setup(5).plan().placements(), setup(5).plan().placements());
    }

    @Test
    void everyBossCanBeReachedByKillingBossesInSomeOrder() {
        for (long seed = 0; seed < 200; seed++) {
            Setup s = setup(seed);
            BossEncounter e = new BossEncounter(s.layout(), s.areas(), s.plan());
            boolean progress = true;
            Set<String> dead = new HashSet<>();
            while (progress) {
                progress = false;
                for (BossPlacement p : s.plan().placements()) {
                    if (!dead.contains(p.def().factionId()) && e.reachableAreas().contains(p.area())) {
                        e.onBossKilled(p.def().factionId(), CRAWLER);
                        dead.add(p.def().factionId());
                        progress = true;
                    }
                }
            }
            assertEquals(s.plan().placements().size(), dead.size(), "seed " + seed + " is solvable");
            assertEquals(s.layout().areaCount(), e.reachableAreas().size(), "all areas open once bosses are dead");
        }
    }

    @Test
    void killingABossOpensTheGatesBehindItsFactionAndGrantsItsLootBox() {
        for (long seed = 0; seed < 60; seed++) {
            Setup s = setup(seed);
            BossEncounter e = new BossEncounter(s.layout(), s.areas(), s.plan());
            BossPlacement p = s.plan().placements().get(0);
            Optional<BossKill> kill = e.onBossKilled(p.def().factionId(), CRAWLER);
            assertTrue(kill.isPresent());
            for (Gate g : s.layout().gates()) {
                boolean behindThisFaction = s.areas().factionOf(g.parentArea()).orElseThrow().id().equals(p.def().factionId());
                assertEquals(behindThisFaction, kill.get().openedGates().contains(g));
                assertEquals(!behindThisFaction, e.isGateClosed(g));
            }
            assertEquals(new Event.LootBoxGranted(CRAWLER, p.def().tier(), LootSource.BOSS_KILL), kill.get().loot());
        }
    }

    @Test
    void aBossIsKilledOnlyOnce() {
        Setup s = setup(3);
        BossEncounter e = new BossEncounter(s.layout(), s.areas(), s.plan());
        String id = s.plan().placements().get(0).def().factionId();
        assertTrue(e.onBossKilled(id, CRAWLER).isPresent());
        assertTrue(e.onBossKilled(id, CRAWLER).isEmpty());
        assertTrue(e.onBossKilled("no_such_faction", CRAWLER).isEmpty());
    }

    @Test
    void onlyRevealingBossesRevealAStairwellAndNeverTheStartAreaOne() {
        int revealed = 0;
        int silent = 0;
        for (long seed = 0; seed < 40; seed++) {
            Setup s = setup(seed);
            for (BossPlacement p : s.plan().placements()) {
                BossEncounter e = new BossEncounter(s.layout(), s.areas(), s.plan());
                BossKill kill = e.onBossKilled(p.def().factionId(), CRAWLER).orElseThrow();
                if (p.def().revealsStairwell()) {
                    Stairwell well = kill.revealedStairwell().orElseThrow();
                    assertTrue(s.layout().stairwells().contains(well));
                    assertNotEquals(0, s.layout().cell(well.cell()).orElseThrow().area());
                    assertTrue(e.isRevealed(well));
                    revealed++;
                } else {
                    assertTrue(kill.revealedStairwell().isEmpty());
                    silent++;
                }
            }
        }
        assertTrue(revealed > 0 && silent > revealed, "some bosses reveal, most do not");
    }

    @Test
    void aStairwellIsReachableWithoutKillingAnyBoss() {
        for (long seed = 0; seed < 100; seed++) {
            Setup s = setup(seed);
            BossEncounter e = new BossEncounter(s.layout(), s.areas(), s.plan());
            boolean found = false;
            for (Stairwell w : s.layout().stairwells()) {
                if (e.reachableAreas().contains(s.layout().cell(w.cell()).orElseThrow().area())) {
                    found = true;
                }
            }
            assertTrue(found, "seed " + seed);
            assertFalse(e.reachableAreas().isEmpty());
        }
    }
}
