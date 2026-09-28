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

import com.fpetrola.emulation.helpers.snapshots.SnapshotFile;
import dev.crystal.plugins.api.Answers;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * A format says twice which files it reads: in {@code @Answers}, which the build writes into the
 * plugin so the emulator can ask before loading it, and in {@code reads()}, which answers once it
 * is loaded. The two have to say the same, in any case of letters.
 */
class AFormatAnswersForWhatItReadsTest {

  private static final List<String> EXTENSIONS = List.of("z80", "sna", "sp", "szx", "tap", "tzx", "dsk", "rzx");

  @TestFactory
  Stream<DynamicTest> everyFormatOfThisPlugin() {
    return Stream.of("sna", "sp", "szx").map(TodaysFormats::format).map(format -> dynamicTest(format.label(), () -> {
      Set<String> answers = Arrays.stream(format.getClass().getAnnotationsByType(Answers.class))
          .flatMap(answer -> Arrays.stream(answer.value())).collect(Collectors.toSet());
      for (String extension : EXTENSIONS) {
        assertEquals(answers.contains(extension), reads(format, "game." + extension), extension);
        assertEquals(answers.contains(extension), reads(format, "GAME." + extension.toUpperCase()), extension.toUpperCase());
      }
    }));
  }

  private static boolean reads(SnapshotFile format, String name) {
    return format.reads(new File(name));
  }
}
