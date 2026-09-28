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
 * What today's readers make of every snapshot, against what libspectrum makes of it, in the
 * fields both can say and the format has a place for. Where they part company is written down in
 * known/read-differences.txt: each line there is a decision still to be taken, and the day the
 * readers are replaced it is the list of what the replacement is allowed to change.
 */
class EveryFixtureReadsAsTheReferenceReadsItTest {

  @BeforeAll
  static void needsLibspectrum() {
    assumeTrue(Reference.present(), "libspectrum is not installed here");
  }

  @Test
  void theyPartCompanyOnlyWhereItIsWrittenDown() {
    List<String> found = new ArrayList<>();
    for (Path fixture : Fixtures.ofTodaysFormats()) {
      String format = TodaysFormats.extensionOf(fixture);
      if (!Reference.asks(format)) {
        continue;
      }
      String name = Fixtures.nameOf(fixture);
      Fields java = TodaysFormats.read(fixture);
      Fields reference = Reference.read(Fixtures.bytes(fixture), format);
      if (java.isRefused() || reference.isRefused()) {
        if (java.isRefused() != reference.isRefused()) {
          found.add(name + ": " + (java.isRefused() ? "java refuses it (" + java.refusal() + "), libspectrum reads it"
              : "libspectrum refuses it, java reads it"));
        }
        continue;
      }
      if (!java.absentParts().isEmpty()) {
        found.add(name + ": java reads no " + String.join(" and no ", java.absentParts()));
        continue;
      }
      java.carriedBy(format).differencesFrom(reference.carriedBy(format))
          .forEach(difference -> found.add(name + ": " + difference));
    }
    Differences.areTheKnownOnes("read-differences.txt", found);
  }
}
