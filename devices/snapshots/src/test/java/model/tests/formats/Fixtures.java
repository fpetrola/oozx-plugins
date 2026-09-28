/*
 * Copyright (c) 2023-2026 Fernando Damian Petrola
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package model.tests.formats;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The snapshots the net is made of, and where what was said about each one is kept.
 * <p>
 * The files are in this module and not referred to where they came from: the test this replaces
 * looked for libspectrum's corpus two directories up, found nothing there, and passed.
 */
final class Fixtures {

  static final Path HERE = Path.of("src/test/resources/snapshots");
  static final Path GOLDENS = HERE.resolve("goldens");
  static final Path KNOWN = HERE.resolve("known");

  private Fixtures() {
  }

  /** Every snapshot here, the corpus and the ones made from it, in a stable order. */
  static List<Path> all() {
    try (Stream<Path> files = Files.walk(HERE)) {
      return files.filter(Files::isRegularFile)
          .filter(file -> Reference.TYPES.containsKey(TodaysFormats.extensionOf(file)))
          .sorted()
          .toList();
    } catch (IOException cannotList) {
      throw new UncheckedIOException(cannotList);
    }
  }

  static String nameOf(Path fixture) {
    return HERE.relativize(fixture).toString();
  }

  static byte[] bytes(Path fixture) {
    try {
      return Files.readAllBytes(fixture);
    } catch (IOException cannotRead) {
      throw new UncheckedIOException(cannotRead);
    }
  }

  /** Where the golden of that kind for that fixture is: goldens/szx-chunks/AY.szx.read.txt. */
  static Path golden(Path fixture, String kind) {
    return GOLDENS.resolve(nameOf(fixture) + "." + kind + ".txt");
  }

  static String text(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException cannotRead) {
      throw new UncheckedIOException(cannotRead);
    }
  }

  static void write(Path file, String text) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, text, StandardCharsets.UTF_8);
    } catch (IOException cannotWrite) {
      throw new UncheckedIOException(cannotWrite);
    }
  }

  /** A list kept in the repository, one entry per line, with # for comments and blank lines ignored. */
  static List<String> known(String name) {
    Path file = KNOWN.resolve(name);
    if (!Files.exists(file)) {
      return List.of();
    }
    return text(file).lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
  }
}
