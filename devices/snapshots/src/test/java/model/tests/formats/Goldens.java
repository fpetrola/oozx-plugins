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

import java.nio.file.Path;

/**
 * What is said of each fixture, the same way when the goldens are written and when they are
 * checked: what today's reader leaves in the state, and what today's writers make of it.
 */
final class Goldens {

  private Goldens() {
  }

  static String read(Path fixture) {
    return TodaysFormats.read(fixture).toString();
  }

  /**
   * Each writer's output, as the bytes themselves where nothing compresses them and as what reading
   * them back says. The SZX's pages go through zlib, whose bytes are the library's business and not
   * this format's, so for it only the reading back is kept. Empty for a fixture nobody reads.
   */
  static String written(Path fixture) {
    if (TodaysFormats.read(fixture).isRefused()) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    for (String format : TodaysFormats.WRITERS) {
      text.append("# ").append(format).append('\n');
      try {
        SpectrumState state = TodaysFormats.load(fixture);
        byte[] bytes = TodaysFormats.write(state, format);
        if (!format.equals("szx")) {
          text.append("bytes = ").append(Fields.digest(bytes)).append('\n');
        }
        text.append(TodaysFormats.read(bytes, format));
      } catch (SnapshotException refused) {
        text.append("refused = ").append(refused.getMessage()).append('\n');
      } catch (RuntimeException crashed) {
        text.append("refused = crashed: ").append(crashed.getClass().getSimpleName()).append('\n');
      }
    }
    return text.toString();
  }
}
