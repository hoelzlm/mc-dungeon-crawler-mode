package dev.crawlmode.checks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConstantsTest {
    private static final Pattern STRING_CONSTANT =
            Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\")\\s*;");

    private static List<Path> javaFiles(boolean includeTests) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String dir : System.getProperty("crawl.source.dirs").split(File.pathSeparator)) {
            if (!includeTests && dir.endsWith("test" + File.separator + "java")) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(Path.of(dir))) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files;
    }

    @Test
    void noStringLiteralIsDeclaredAsAConstantTwice() throws IOException {
        Map<String, List<String>> declarations = new TreeMap<>();
        for (Path file : javaFiles(true)) {
            Matcher m = STRING_CONSTANT.matcher(Files.readString(file));
            while (m.find()) {
                declarations.computeIfAbsent(m.group(2), k -> new ArrayList<>())
                        .add(file.getFileName() + "." + m.group(1));
            }
        }
        List<String> duplicates = new ArrayList<>();
        declarations.forEach((literal, where) -> {
            if (where.size() > 1) {
                duplicates.add(literal + " declared in " + where);
            }
        });
        assertTrue(duplicates.isEmpty(), "Duplicate string constants, keep one source of truth:\n" + String.join("\n", duplicates));
    }

    @Test
    void modIdAndHostPrefixAppearInExactlyOneProductionLiteral() throws IOException {
        assertSingleOccurrence("\"crawlmode\"");
        assertSingleOccurrence("\"[The Host]");
    }

    private static void assertSingleOccurrence(String literal) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : javaFiles(false)) {
            int count = Files.readString(file).split(Pattern.quote(literal), -1).length - 1;
            if (count > 0) {
                found.add(file.getFileName() + " x" + count);
            }
        }
        assertTrue(found.size() == 1 && found.get(0).endsWith("x1"), literal + " must be defined once, found: " + found);
    }
}
