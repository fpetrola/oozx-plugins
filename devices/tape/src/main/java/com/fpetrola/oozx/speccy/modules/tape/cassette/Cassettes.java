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

package com.fpetrola.oozx.speccy.modules.tape.cassette;

import com.fpetrola.oozx.plugins.Plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

/** A file as a cassette, read by whichever tape format answers for its name; nothing if none does or it is not one. */
public final class Cassettes {

  private Cassettes() {
  }

  public static Optional<Cassette> read(File file) {
    try {
      return read(file.getName(), Files.readAllBytes(file.toPath()));
    } catch (IOException cannotRead) {
      return Optional.empty();
    }
  }

  public static Optional<Cassette> read(String name, byte[] bytes) {
    File named = new File(name);
    for (TapeFormat format : Plugins.found(TapeFormat.class)) {
      if (format.reads(named)) {
        try {
          return Optional.of(format.read(bytes));
        } catch (TapeRefused notOne) {
          return Optional.empty();
        }
      }
    }
    return Optional.empty();
  }

  /** Whether some tape format answers for files with that name. */
  public static boolean isATape(String name) {
    File named = new File(name);
    return Plugins.found(TapeFormat.class).stream().anyMatch(format -> format.reads(named));
  }
}
