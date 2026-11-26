package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.boss.BossEncounter;
import dev.crawlmode.engine.boss.BossPlacement;
import dev.crawlmode.engine.boss.BossPlan;
import dev.crawlmode.engine.boss.Bosses;
import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.faction.Factions;
import dev.crawlmode.engine.faction.PlannedSpawn;
import dev.crawlmode.engine.faction.SpawnPlanner;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FloorThreeTest {
    private static final Factions FACTIONS = factions();
    private static final Bosses BOSSES = bosses();
    private static final TemplateLibrary LIBRARY = library();

    private static Factions factions() {
        return Factions.load(new StringReader(FloorSpec.readFactionData("factions")));
    }

    private static Bosses bosses() {
        return Bosses.load(new StringReader(FloorSpec.readFactionData("bosses")), FACTIONS);
    }

    private static TemplateLibrary library() {
        try (var in = FloorThreeTest.class.getResourceAsStream("/data/crawlmode/floor/floor3_templates.txt")) {
            return TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void bundledTemplatesCoverVillagesRuinsPathsAndStairwellRooms() {
        assertTrue(LIBRARY.villages().size() >= 3);
        assertTrue(LIBRARY.rooms().size() >= 3);
        assertTrue(LIBRARY.corridors().size() >= 2);
        assertFalse(LIBRARY.stairwellRooms().isEmpty());
    }

    @Test
    void floorThreeSpecBuildsTheVillageAndRuinsLayoutWithThreeStairwells() {
        FloorSpec spec = FloorSpec.forFloor(3).orElseThrow();
        for (long seed = 0; seed < 20; seed++) {
            FloorLayout layout = FloorLayout.generate(seed, LIBRARY, spec);
            assertEquals(spec.areas(), layout.areaCount());
            assertEquals(3, layout.stairwells().size());
            assertTrue(layout.cells().stream().anyMatch(c -> c.template().role() == FloorTemplate.Role.VILLAGE));
        }
    }

    @Test
    void floorThreeHasThreeFactionsWithABossEach() {
        assertEquals(Set.of("ghost_bandits", "wild_beasts", "ash_cult"),
                FACTIONS.forFloor(3).stream().map(f -> f.id()).collect(Collectors.toSet()));
        FACTIONS.forFloor(3).forEach(f -> assertTrue(BOSSES.of(f.id()) != null));
    }

    @Test
    void villagesNeverGetHostileSpawnsAndRuinsHoldEveryFaction() {
        for (long seed = 0; seed < 40; seed++) {
            FloorLayout layout = VillageRuinsLayout.generate(seed, LIBRARY);
            FactionAreas areas = FactionAreas.assign(FACTIONS.forFloor(3), layout.areaCount());
            Set<String> holders = new HashSet<>();
            for (Cell cell : layout.cells()) {
                List<PlannedSpawn> spawns = SpawnPlanner.forCell(cell, areas, seed);
                if (cell.template().role() == FloorTemplate.Role.VILLAGE) {
                    assertTrue(spawns.isEmpty(), "village cell " + cell.pos() + " seed " + seed);
                }
                spawns.forEach(s -> holders.add(s.mob().factionId()));
            }
            assertEquals(3, holders.size(), "seed " + seed);
        }
    }

    @Test
    void everyBossStandsInARuinRoomAndKillingThemInTurnOpensEveryGate() {
        for (long seed = 0; seed < 40; seed++) {
            FloorLayout layout = VillageRuinsLayout.generate(seed, LIBRARY);
            FactionAreas areas = FactionAreas.assign(FACTIONS.forFloor(3), layout.areaCount());
            BossPlan plan = BossPlan.generate(layout, areas, FACTIONS.forFloor(3), BOSSES, seed);
            assertEquals(3, plan.placements().size());
            for (BossPlacement p : plan.placements()) {
                Cell cell = layout.cell(new CellPos(Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16))).orElseThrow();
                assertEquals(FloorTemplate.Role.ROOM, cell.template().role());
                assertTrue(cell.area() > 0);
            }
            BossEncounter encounter = new BossEncounter(layout, areas, plan);
            Set<String> killed = new HashSet<>();
            boolean progress = true;
            while (progress) {
                progress = false;
                for (BossPlacement p : plan.placements()) {
                    if (!killed.contains(p.def().factionId()) && encounter.reachableAreas().contains(p.area())) {
                        encounter.onBossKilled(p.def().factionId(), UUID.randomUUID());
                        killed.add(p.def().factionId());
                        progress = true;
                    }
                }
            }
            assertEquals(3, killed.size(), "seed " + seed);
            assertTrue(layout.gates().stream().noneMatch(encounter::isGateClosed));
            assertTrue(plan.placements().stream().anyMatch(p -> p.reveals().isPresent()));
        }
    }

    @Test
    void villageShopsAreDecorativeOnly() {
        Set<String> interactive = Set.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
                "minecraft:ender_chest", "minecraft:lectern", "minecraft:cartography_table", "minecraft:smithing_table",
                "minecraft:fletching_table", "minecraft:loom", "minecraft:grindstone", "minecraft:stonecutter",
                "minecraft:blast_furnace", "minecraft:smoker", "minecraft:furnace", "minecraft:brewing_stand",
                "minecraft:composter", "minecraft:cauldron", "minecraft:crafting_table", "minecraft:anvil",
                "minecraft:enchanting_table", "minecraft:spawner", "minecraft:command_block", "minecraft:jukebox",
                "minecraft:bell", "minecraft:shulker_box", "minecraft:hopper", "minecraft:dispenser", "minecraft:dropper");
        for (FloorTemplate t : LIBRARY.villages()) {
            for (int y = 0; y < t.height(); y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        t.blockAt(x, y, z, Set.of()).ifPresent(b -> assertFalse(interactive.contains(b.blockId()),
                                t.name() + " uses " + b.blockId()));
                    }
                }
            }
        }
    }

    @Test
    void everyVillageTemplateHasShopStructures() {
        for (FloorTemplate t : LIBRARY.villages()) {
            boolean built = false;
            for (int z = 0; z < 16 && !built; z++) {
                for (int x = 0; x < 16 && !built; x++) {
                    built = t.blockAt(x, 1, z, Set.of()).isPresent();
                }
            }
            assertTrue(built, t.name() + " has no structures");
        }
    }

    @Test
    void ruinRoomsLeaveSpawnSpotsClearWithinTwoBlocks() {
        for (FloorTemplate t : LIBRARY.rooms()) {
            for (int z = 2; z <= 13; z++) {
                for (int x = 2; x <= 13; x++) {
                    boolean solidTo3 = t.blockAt(x, 1, z, Set.of()).isPresent() && t.blockAt(x, 2, z, Set.of()).isPresent()
                            && t.blockAt(x, 3, z, Set.of()).isPresent();
                    assertFalse(solidTo3, t.name() + " blocks " + x + "," + z);
                }
            }
        }
    }
}
