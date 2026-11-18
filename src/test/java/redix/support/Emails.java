// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What the Dev server would have sent: it keeps each email as a file in its folder instead (redix-api #127 D7), named in the order sent.
 * REDIX_EMAIL_FOLDER is that folder, the server's REDIX_Dev_Email__Folder.
 */
public final class Emails {
    private static final @NotNull Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private Emails() {
    }

    /** Every email kept to address, oldest first. */
    public static @NotNull List<String> to(final @NotNull String address) {
        final @NotNull Path folder = Path.of(System.getenv().getOrDefault("REDIX_EMAIL_FOLDER", "emails"));
        try (final Stream<Path> files = Files.list(folder)) {
            return files.sorted(Comparator.comparing(Path::getFileName)).map(Emails::read).filter(text -> text.startsWith("To: " + address + "\n")).toList();
        } catch (final IOException listing) {
            throw new UncheckedIOException(listing);
        }
    }

    /**
     * The email to address after the before it already had, waited for up to ten seconds: the API answers before it makes and sends a code
     * (redix-api #127 R5), so the email arrives after the answer.
     */
    public static @NotNull String next(final @NotNull String address, final int before) {
        final long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            final @NotNull List<String> emails = to(address);
            if (emails.size() > before) {
                return emails.getLast();
            }
            try {
                Thread.sleep(100);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("no email reached " + address + " within ten seconds");
    }

    /** How many emails the folder holds, to anyone. */
    public static int total() {
        try (final Stream<Path> files = Files.list(Path.of(System.getenv().getOrDefault("REDIX_EMAIL_FOLDER", "emails")))) {
            return (int) files.count();
        } catch (final IOException listing) {
            throw new UncheckedIOException(listing);
        }
    }

    /** Waits a second: longer than an email the API sends after its answer takes to arrive, so one that has not arrived by then was not sent. */
    public static void settle() {
        try {
            Thread.sleep(1000);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** The six-digit code email carries. */
    public static @NotNull String codeIn(final @NotNull String email) {
        final @NotNull Matcher code = CODE.matcher(email);
        return Optional.of(code).filter(Matcher::find).map(found -> found.group(1)).orElseThrow(() -> new AssertionError("no code in " + email));
    }

    private static @NotNull String read(final @NotNull Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (final IOException reading) {
            throw new UncheckedIOException(reading);
        }
    }
}
