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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;

/**
 * Writes down what today's formats do with every fixture. Run by hand, once, and never to make a
 * test pass: a golden changes in a commit that says why.
 * <pre>
 * mvn -pl devices/snapshots test -Dtest=WriteTheGoldens -Dsnapshots.writeGoldens=true
 * </pre>
 */
@EnabledIfSystemProperty(named = "snapshots.writeGoldens", matches = "true")
class WriteTheGoldens {

  @Test
  void write() {
    for (Path fixture : Fixtures.all()) {
      Fixtures.write(Fixtures.golden(fixture, "read"), Goldens.read(fixture));
      String written = Goldens.written(fixture);
      if (!written.isEmpty()) {
        Fixtures.write(Fixtures.golden(fixture, "written"), written);
      }
    }
  }
}
