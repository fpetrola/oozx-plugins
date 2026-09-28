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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The differences found, held to the ones written down in a file under known/. A difference that
 * is not written down fails, and so does one that is written down and no longer happens: the list
 * says what is true today, or it says nothing.
 */
final class Differences {

  private Differences() {
  }

  static void areTheKnownOnes(String known, List<String> found) {
    Set<String> expected = new LinkedHashSet<>(Fixtures.known(known));
    Set<String> actual = new LinkedHashSet<>(found);
    List<String> appeared = new ArrayList<>(actual);
    appeared.removeAll(expected);
    List<String> gone = new ArrayList<>(expected);
    gone.removeAll(actual);
    if (!appeared.isEmpty() || !gone.isEmpty()) {
      fail("known/" + known + " is not what happens:\n"
          + (appeared.isEmpty() ? "" : "  new, not written down:\n    " + String.join("\n    ", appeared) + "\n")
          + (gone.isEmpty() ? "" : "  written down, no longer happening:\n    " + String.join("\n    ", gone) + "\n"));
    }
  }
}
