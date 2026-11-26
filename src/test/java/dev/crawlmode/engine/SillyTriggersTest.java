package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SillyTriggersTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void damageTypesBecomeDeathTriggers() {
        assertEquals(Optional.of("death.lava"), SillyTriggers.death("minecraft:lava"));
        assertEquals(Optional.of("death.drown"), SillyTriggers.death("minecraft:drown"));
        assertEquals(Optional.of("death.cactus"), SillyTriggers.death("minecraft:cactus"));
        assertEquals(Optional.of("death.fire"), SillyTriggers.death("minecraft:on_fire"));
        assertEquals(Optional.of("death.fire"), SillyTriggers.death("minecraft:in_fire"));
        assertEquals(Optional.of("death.explosion"), SillyTriggers.death("minecraft:explosion"));
        assertEquals(Optional.empty(), SillyTriggers.death("minecraft:player_attack"));
    }

    @Test
    void killingInnocentsAndEatingJunkHaveTriggers() {
        assertEquals(Optional.of("kill.chicken"), SillyTriggers.kill("minecraft:chicken"));
        assertEquals(Optional.empty(), SillyTriggers.kill("minecraft:zombie"));
        assertEquals(Optional.of("eat.rotten-flesh"), SillyTriggers.eat("minecraft:rotten_flesh"));
        assertEquals(Optional.of("eat.cake"), SillyTriggers.eat("minecraft:cake"));
        assertEquals(Optional.empty(), SillyTriggers.eat("minecraft:bread"));
    }

    @Test
    void countersFireAtMilestonesOncePerCrawler() {
        var tally = new SillyTriggers.Tally();
        List<String> all = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            all.addAll(tally.count(CARL, "jump"));
        }
        assertEquals(List.of("jump.100"), all);
        assertTrue(tally.count(CARL, "jump").isEmpty());
        assertTrue(tally.count(DONUT, "jump").isEmpty());
        for (int i = 0; i < 898; i++) {
            tally.count(CARL, "jump");
        }
        assertEquals(List.of("jump.1000"), tally.count(CARL, "jump"));
    }

    @Test
    void killTalliesFireAtTen() {
        var tally = new SillyTriggers.Tally();
        for (int i = 0; i < 9; i++) {
            assertTrue(tally.count(CARL, "kill.grunt").isEmpty());
        }
        assertEquals(List.of("kill.grunt.10"), tally.count(CARL, "kill.grunt"));
    }

    @Test
    void everyTriggerTheDetectorsCanProduceHasAnAchievementAndTheSetIsAboutThirty() {
        var a = Achievements.load(new InputStreamReader(
                SillyTriggersTest.class.getResourceAsStream("/data/crawlmode/achievement/achievements.txt")));
        Set<String> triggers = new HashSet<>();
        a.all().forEach(x -> triggers.add(x.trigger()));
        assertTrue(a.all().size() >= 28 && a.all().size() <= 34, "size " + a.all().size());
        assertTrue(triggers.containsAll(SillyTriggers.allTriggers()), "missing: "
                + SillyTriggers.allTriggers().stream().filter(t -> !triggers.contains(t)).toList());
    }
}
