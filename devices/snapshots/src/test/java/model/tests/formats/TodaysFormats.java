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
import com.fpetrola.emulation.helpers.snapshots.SnapshotFile;
import com.fpetrola.emulation.helpers.snapshots.SnapshotSNA;
import com.fpetrola.emulation.helpers.snapshots.SnapshotSZX;
import com.fpetrola.emulation.helpers.snapshots.SnapshotZ80;
import com.fpetrola.emulation.helpers.snapshots.SpectrumState;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The four formats as the build has them today, called by name and not found through the
 * factory: the net is about what each one does, not about which one a file ends up with.
 */
final class TodaysFormats {

  private static final Map<String, Supplier<SnapshotFile>> BY_EXTENSION = Map.of(
      "z80", SnapshotZ80::new, "sna", SnapshotSNA::new, "szx", SnapshotSZX::new);

  /** The old readers this plugin still has: one goes when the format that walks the machine replaces it. */
  static final List<String> OF_THIS_PLUGIN = List.of("sna", "szx");

  /** Whether an old reader still reads files with that extension. */
  static boolean has(String extension) {
    return BY_EXTENSION.containsKey(extension);
  }

  /** The ones that write: SP never did. */
  static final List<String> WRITERS = List.of("sna", "szx", "z80");

  private TodaysFormats() {
  }

  static String extensionOf(Path file) {
    String name = file.getFileName().toString();
    return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
  }

  /** A fresh reader for that format: they keep what they read in their own fields. */
  static SnapshotFile format(String extension) {
    return BY_EXTENSION.get(extension).get();
  }

  static SpectrumState load(Path file) throws SnapshotException {
    return format(extensionOf(file)).load(file.toFile());
  }

  /** What today's reader of that file leaves in the state, or how it refuses it - and a crash of the reader said as one. */
  static Fields read(Path file) {
    SpectrumState state;
    try {
      state = load(file);
    } catch (SnapshotException refused) {
      return Fields.refused(refused.getMessage());
    } catch (RuntimeException crashed) {
      return Fields.refused("crashed: " + crashed.getClass().getSimpleName());
    }
    return Fields.of(state);
  }

  /** What today's reader of that format says of those bytes, through a file where the reader only knows files. */
  static Fields read(byte[] image, String extension) {
    try {
      Path file = Files.createTempFile("image", "." + extension);
      try {
        Files.write(file, image);
        return read(file);
      } finally {
        Files.delete(file);
      }
    } catch (IOException cannotWrite) {
      throw new IllegalStateException(cannotWrite);
    }
  }

  /** That state written by today's writer of the format, through a file where the writer only knows files. */
  static byte[] write(SpectrumState state, String extension) throws SnapshotException {
    if (extension.equals("z80")) {
      return new SnapshotZ80().saveToBytes(state);
    }
    try {
      File written = File.createTempFile("written", "." + extension);
      try {
        format(extension).save(written, state);
        return Files.readAllBytes(written.toPath());
      } finally {
        written.delete();
      }
    } catch (IOException cannotWrite) {
      throw new IllegalStateException(cannotWrite);
    }
  }
}
