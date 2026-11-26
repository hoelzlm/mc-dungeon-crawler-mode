package dev.crawlmode.checks;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private static final String ADAPTER_PACKAGE = "dev.crawlmode.adapter";
    private static final String HUB = "CrawlAdapter";

    private static final Set<String> SHARED_HELPERS = Set.of("Floors", "HostChat");

    private static final Map<String, Set<String>> ALLOWED_ADAPTER_DEPENDENCIES = Map.of(
            "AidAdapter", Set.of("TemplateFloorAdapter"),
            "FactionMobs", Set.of("AchievementAdapter"),
            "LootBoxAdapter", Set.of("AidAdapter"),
            "TemplateFloorAdapter", Set.of("BossAdapter", "FactionMobs"),
            "BossAdapter", Set.of("FactionMobs"),
            "StairwellAdapter", Set.of("TimerHud"));

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
}
