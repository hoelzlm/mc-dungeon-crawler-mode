package dev.crawlmode.checks;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.File;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private static final String ADAPTER_PACKAGE = "dev.crawlmode.adapter";
    private static final String HUB = "CrawlAdapter";

    private static final Set<String> SHARED_HELPERS = Set.of("Floors", "HostChat", "Inventories");

    private static final Map<String, Set<String>> ALLOWED_ADAPTER_DEPENDENCIES = Map.of(
            "AidAdapter", Set.of("TemplateFloorAdapter"),
            "FactionMobs", Set.of("AchievementAdapter"),
            "LootBoxAdapter", Set.of("AidAdapter"),
            "TemplateFloorAdapter", Set.of("BossAdapter", "FactionMobs", "SafeRoomAdapter"),
            "BossAdapter", Set.of("FactionMobs"),
            "StairwellAdapter", Set.of("TimerHud"));

    private static final Set<String> REFLECTION_ALLOWED = Set.of("SafeRoomAdapter");

    private static final Set<String> STATIC_STATE_BASELINE = Set.of(
            "dev.crawlmode.adapter.CrawlAdapter.timerHud",
            "dev.crawlmode.adapter.CrawlAdapter.server",
            "dev.crawlmode.adapter.CrawlAdapter.engine",
            "dev.crawlmode.adapter.CrawlAdapter.elimination",
            "dev.crawlmode.adapter.TemplateFloorAdapter.FLOORS",
            "dev.crawlmode.adapter.TemplateFloorAdapter.STATES",
            "dev.crawlmode.adapter.LootBoxAdapter.BOXES",
            "dev.crawlmode.adapter.FactionMobs.factions",
            "dev.crawlmode.adapter.SafeRoomAdapter.PENDING_ENTRIES",
            "dev.crawlmode.adapter.SafeRoomAdapter.DOORS",
            "dev.crawlmode.adapter.SafeRoomAdapter.ROOMS",
            "dev.crawlmode.adapter.BossAdapter.FLOORS",
            "dev.crawlmode.adapter.AidAdapter.ACTIVE",
            "dev.crawlmode.adapter.AidAdapter.BY_ID");

    private static final String MAP_RECT = "dev.crawlmode.engine.map.MapRect";

    private static final Set<String> MAP_RECT_BASELINE = Set.of("BossAdapter");

    private static JavaClasses classesIn(String property) {
        List<Path> paths = new ArrayList<>();
        for (String dir : System.getProperty(property).split(File.pathSeparator)) {
            paths.add(Path.of(dir));
        }
        return new ClassFileImporter().importPaths(paths);
    }

    private static String topLevel(JavaClass type) {
        JavaClass outer = type;
        while (outer.getEnclosingClass().isPresent()) {
            outer = outer.getEnclosingClass().get();
        }
        return outer.getSimpleName();
    }

    @Test
    void engineNeverDependsOnMinecraftOrNeoForge() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("net.minecraft..", "net.neoforged..")
                .check(classesIn("crawl.engine.classes"));
    }

    @Test
    void adaptersDependOnEachOtherOnlyThroughTheHubOrTheAllowlist() {
        List<String> violations = new ArrayList<>();
        for (JavaClass type : classesIn("crawl.main.classes")) {
            if (!type.getPackageName().equals(ADAPTER_PACKAGE)) {
                continue;
            }
            String from = topLevel(type);
            if (from.equals(HUB)) {
                continue;
            }
            for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (!target.getPackageName().equals(ADAPTER_PACKAGE)) {
                    continue;
                }
                String to = topLevel(target);
                boolean allowed = to.equals(from)
                        || SHARED_HELPERS.contains(to)
                        || ALLOWED_ADAPTER_DEPENDENCIES.getOrDefault(from, Set.of()).contains(to);
                if (!allowed) {
                    violations.add(from + " -> " + to + " (" + dependency.getDescription() + ")");
                }
            }
        }
        assertTrue(violations.isEmpty(), "Adapter dependencies outside the allowlist:\n" + String.join("\n", violations));
    }

    @Test
    void noConsoleOutputOrPrintStackTrace() {
        JavaClasses production = new ClassFileImporter().importPaths(Stream.of("crawl.engine.classes", "crawl.main.classes")
                .flatMap(p -> Stream.of(System.getProperty(p).split(File.pathSeparator)))
                .map(Path::of)
                .toList());
        noClasses()
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .orShould().callMethod(Throwable.class, "printStackTrace")
                .check(production);
    }

    @Test
    void adaptersAddNoStaticMutableState() {
        Set<String> found = new TreeSet<>();
        for (JavaClass type : classesIn("crawl.main.classes")) {
            if (!type.getPackageName().equals(ADAPTER_PACKAGE)) {
                continue;
            }
            for (JavaField field : type.getFields()) {
                if (!field.getModifiers().contains(JavaModifier.STATIC) || field.getModifiers().contains(JavaModifier.SYNTHETIC)) {
                    continue;
                }
                boolean mutable = !field.getModifiers().contains(JavaModifier.FINAL)
                        || field.getRawType().isAssignableTo(Collection.class)
                        || field.getRawType().isAssignableTo(Map.class);
                if (mutable) {
                    found.add(field.getFullName());
                }
            }
        }
        List<String> violations = new ArrayList<>();
        found.stream().filter(name -> !STATIC_STATE_BASELINE.contains(name)).forEach(name -> violations.add("new: " + name));
        STATIC_STATE_BASELINE.stream().filter(name -> !found.contains(name)).forEach(name -> violations.add("gone, remove from baseline: " + name));
        assertTrue(violations.isEmpty(), "Static mutable state in adapters (CODING_STANDARDS: State has an owner; the baseline only shrinks):\n"
                + String.join("\n", violations));
    }

    @Test
    void adaptersUseNoReflectionOutsideTheAllowlist() {
        List<String> violations = new ArrayList<>();
        for (JavaClass type : classesIn("crawl.main.classes")) {
            if (!type.getPackageName().equals(ADAPTER_PACKAGE) || REFLECTION_ALLOWED.contains(topLevel(type))) {
                continue;
            }
            for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (target.isAssignableTo(Field.class) || target.isAssignableTo(Method.class)
                        || target.isAssignableTo(AccessibleObject.class)) {
                    violations.add(dependency.getDescription());
                }
            }
        }
        assertTrue(violations.isEmpty(), "Reflection in adapters outside the allowlist (ADR 0006):\n"
                + String.join("\n", violations));
    }

    @Test
    void adaptersDoNotConstructMapRect() {
        Set<String> found = new TreeSet<>();
        for (JavaClass type : classesIn("crawl.main.classes")) {
            if (!type.getPackageName().equals(ADAPTER_PACKAGE)) {
                continue;
            }
            boolean constructs = type.getConstructorCallsFromSelf().stream()
                    .anyMatch(call -> call.getTargetOwner().getName().equals(MAP_RECT));
            if (constructs) {
                found.add(topLevel(type));
            }
        }
        List<String> violations = new ArrayList<>();
        found.stream().filter(name -> !MAP_RECT_BASELINE.contains(name)).forEach(name -> violations.add("new: " + name));
        MAP_RECT_BASELINE.stream().filter(name -> !found.contains(name)).forEach(name -> violations.add("gone, remove from baseline: " + name));
        assertTrue(violations.isEmpty(), "Adapters constructing MapRect (CODING_STANDARDS: The engine decides, adapters translate; the baseline only shrinks):\n"
                + String.join("\n", violations));
    }
}
