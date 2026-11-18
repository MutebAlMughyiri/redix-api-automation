// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redix.support;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;

/** Files a test sends: a picture or a PDF of the size it needs, each unique, so no two tests send the same bytes (UC-021). */
public final class Files {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private Files() {
    }

    /** A PNG of size bytes: its signature, then random bytes. */
    public static byte @NotNull [] png(final int size) {
        final byte[] file = new byte[size];
        ThreadLocalRandom.current().nextBytes(file);
        System.arraycopy(PNG, 0, file, 0, PNG.length);
        return file;
    }

    /** A PDF of at least size bytes: its header, a comment as long as it needs, and its end. */
    public static byte @NotNull [] pdf(final int size) {
        final byte[] padding = new byte[Math.max(0, size - 64)];
        Arrays.fill(padding, (byte) 'x');
        return ("%PDF-1.4\n%" + ThreadLocalRandom.current().nextLong() + " " + new String(padding, StandardCharsets.US_ASCII) + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    /** Sends a file as the multipart file of an upload to path, named name and said to be of type. */
    public static @NotNull Response upload(final @NotNull RequestSpecification as, final @NotNull String path, final @NotNull String name, final byte @NotNull [] file, final @NotNull String type) {
        return given(as).contentType("multipart/form-data").multiPart("file", name, file, type).post(path);
    }
}
