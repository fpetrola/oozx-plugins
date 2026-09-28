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

package model.tests.tapes;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The net under the tapes: every tape here played through, and what it put on the EAR line, as it
 * was when these goldens were written. A tape format is what it sounds like, so this is what
 * every one that replaces another is held to. With -Dtapes.writeGoldens=true it writes them.
 */
class EveryTapeSoundsAsItDidTest {

  static final Path HERE = Path.of("src/test/resources/tapes");
  static final Path GOLDENS = HERE.resolve("goldens");
  static final boolean WRITING = Boolean.getBoolean("tapes.writeGoldens");

  static Stream<Path> tapes() throws IOException {
    return Files.walk(HERE).filter(Files::isRegularFile).filter(file -> !file.startsWith(GOLDENS))
        .filter(file -> file.toString().matches(".*\\.(tap|tzx|csw|pzx)$")).sorted();
  }

  @TestFactory
  Stream<DynamicTest> eachTapeSoundsAsItDid() throws IOException {
    return tapes().map(tape -> DynamicTest.dynamicTest(HERE.relativize(tape).toString(), () -> {
      Path golden = GOLDENS.resolve(HERE.relativize(tape) + ".signal.txt");
      String now = Signal.of(tape.toFile());
      if (WRITING) {
        Files.createDirectories(golden.getParent());
        Files.writeString(golden, now);
      } else {
        assertEquals(Files.readString(golden), now, HERE.relativize(tape).toString());
      }
    }));
  }
}
