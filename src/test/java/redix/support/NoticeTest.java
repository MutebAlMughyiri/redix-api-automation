// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;

/** Every Java file opens with Tech Details' copyright notice, word for word (redix-api #143 R1, R6). */
public class NoticeTest {
    private static final @NotNull List<String> NOTICE = List.of("// Copyright 2026 Tech Details. All rights reserved.",
            "// Use of this file is governed by the LICENSE file at the root of this repository.");

    @Test(description = "Every Java file opens with the copyright notice")
    public void everyJavaFileOpensWithTheNotice() {
        try (final Stream<Path> files = Files.walk(Path.of("src"))) {
            final List<String> missing = files.filter(path -> path.toString().endsWith(".java")).filter(path -> !opensWithTheNotice(path)).map(Path::toString).toList();
            assertEquals(missing, List.of(), "files that open without the notice");
        } catch (final IOException walking) {
            throw new UncheckedIOException(walking);
        }
    }

    private static boolean opensWithTheNotice(final @NotNull Path path) {
        try {
            final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return lines.size() >= NOTICE.size() && lines.subList(0, NOTICE.size()).equals(NOTICE);
        } catch (final IOException reading) {
            throw new UncheckedIOException(reading);
        }
    }
}
