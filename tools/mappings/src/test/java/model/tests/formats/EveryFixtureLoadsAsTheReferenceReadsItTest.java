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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The oracle over the machine: what libspectrum reads of every snapshot, against what the machine
 * is left with once the format loads it, in the fields both say and the format carries. Where they
 * part company is written down in known/machine-differences.txt, each group with its reason. The
 * joystick is left out: the machine does not keep one yet.
 */
class EveryFixtureLoadsAsTheReferenceReadsItTest {

  @BeforeAll
  static void needsLibspectrum() {
    assumeTrue(Reference.present(), "libspectrum is not installed here");
  }

  @Test
  void theyPartCompanyOnlyWhereItIsWrittenDown() {
    List<String> found = new ArrayList<>();
    for (Path fixture : Fixtures.all()) {
      String format = TodaysFormats.extensionOf(fixture);
      if (!Reference.asks(format)) {
        continue;
      }
      String name = Fixtures.nameOf(fixture);
      String loaded = EveryFixtureLoadsIntoTheMachineAsItDidTest.loaded(fixture);
      Fields reference = Reference.read(Fixtures.bytes(fixture), format);
      boolean refused = loaded.startsWith("refused");
      if (refused || reference.isRefused()) {
        if (refused != reference.isRefused()) {
          found.add(name + ": " + (refused ? "the machine refuses it, libspectrum reads it" : "libspectrum refuses it, the machine loads it"));
        }
        continue;
      }
      Fields machine = Fields.parse(saidAbsent(loaded) + loaded);
      machine.carriedBy(format).differencesFrom(reference.carriedBy(format)).stream()
          .filter(difference -> !difference.startsWith("joystick:"))
          .forEach(difference -> found.add(name + ": " + difference));
    }
    Differences.areTheKnownOnes("machine-differences.txt", found);
  }

  /** What a machine does not have is not a line of its description; libspectrum says it absent. */
  private static String saidAbsent(String loaded) {
    StringBuilder absent = new StringBuilder(loaded.contains("ay.registers") ? "ay = fitted\n" : "ay = absent\n");
    for (String name : List.of("port7ffd", "port1ffd", "ay.selected", "ay.registers")) {
      if (!loaded.contains(name + " = ")) absent.append(name).append(" = absent\n");
    }
    for (int page = 0; page < 8; page++) {
      if (!loaded.contains("page." + page + " = ")) absent.append("page.").append(page).append(" = absent\n");
    }
    return absent.toString();
  }
}
