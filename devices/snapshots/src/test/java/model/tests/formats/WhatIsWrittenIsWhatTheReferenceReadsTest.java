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

import com.fpetrola.emulation.helpers.snapshots.SnapshotException;
import com.fpetrola.emulation.helpers.snapshots.SpectrumState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What today's writers write, read by libspectrum: it has to hold what was written, in the fields
 * the format has a place for. Nobody looked at what goes out in SNA or SZX until now; where it
 * does not hold is written down in known/written-differences.txt.
 */
class WhatIsWrittenIsWhatTheReferenceReadsTest {

  @BeforeAll
  static void needsLibspectrum() {
    assumeTrue(Reference.present(), "libspectrum is not installed here");
  }

  @Test
  void whatGoesOutIsWhatWasThereWhereItIsNotWrittenDown() {
    List<String> found = new ArrayList<>();
    for (Path fixture : Fixtures.ofTodaysFormats()) {
      Fields read = TodaysFormats.read(fixture);
      if (read.isRefused() || !read.absentParts().isEmpty()) {
        continue;
      }
      for (String format : TodaysFormats.WRITERS) {
        String name = Fixtures.nameOf(fixture) + " -> " + format;
        byte[] written;
        try {
          SpectrumState state = TodaysFormats.load(fixture);
          written = TodaysFormats.write(state, format);
        } catch (SnapshotException | RuntimeException notWritten) {
          continue;                                   // what refuses or crashes is in the written goldens
        }
        Fields reference = Reference.read(written, format);
        if (reference.isRefused()) {
          found.add(name + ": libspectrum refuses what java wrote");
          continue;
        }
        read.carriedBy(format).differencesFrom(reference.carriedBy(format))
            .forEach(difference -> found.add(name + ": " + difference));
      }
    }
    Differences.areTheKnownOnes("written-differences.txt", found);
  }
}
