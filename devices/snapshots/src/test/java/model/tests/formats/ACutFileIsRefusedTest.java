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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A snapshot cut short - a download that stopped, a disk that filled - is refused with a reason,
 * and never takes the reader down with an exception nobody meant. Where today's readers do crash
 * is written down in known/cut-crashes.txt, one line per file and exception.
 * <p>
 * Not every byte: the whole of every header, and sixty cuts spread over the rest.
 */
class ACutFileIsRefusedTest {

  @Test
  void aCutSnapshotCrashesNothingThatIsNotWrittenDown(@TempDir Path temp) throws IOException {
    List<String> found = new ArrayList<>();
    for (Path fixture : Fixtures.ofTodaysFormats()) {
      if (TodaysFormats.read(fixture).isRefused()) {
        continue;
      }
      byte[] whole = Fixtures.bytes(fixture);
      Path cut = temp.resolve("cut." + TodaysFormats.extensionOf(fixture));
      Map<String, List<Integer>> crashes = new TreeMap<>();
      for (int at : cuts(whole.length)) {
        Files.write(cut, Arrays.copyOf(whole, at));
        Fields read = TodaysFormats.read(cut);
        if (read.crashed()) {
          crashes.computeIfAbsent(read.refusal(), crash -> new ArrayList<>()).add(at);
        }
      }
      crashes.forEach((crash, at) -> found.add(Fixtures.nameOf(fixture) + ": " + at.size() + " cuts "
          + crash + ", the first at " + at.get(0)));
    }
    Differences.areTheKnownOnes("cut-crashes.txt", found);
  }

  private static List<Integer> cuts(int length) {
    TreeSet<Integer> at = new TreeSet<>();
    for (int cut = 0; cut < Math.min(length, 100); cut++) {
      at.add(cut);
    }
    for (int step = 0; step < 60; step++) {
      at.add(100 + (int) ((long) (length - 100) * step / 60));
    }
    for (int cut = Math.max(0, length - 4); cut < length; cut++) {
      at.add(cut);
    }
    at.removeIf(cut -> cut >= length);
    return new ArrayList<>(at);
  }
}
