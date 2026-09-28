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

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.Map;
import java.util.Optional;

/**
 * What libspectrum makes of a snapshot: the fields it reads, or the same snapshot written in
 * another format. Everything that uses it steps aside where the library is not installed.
 */
final class Reference {

  static final Map<String, Integer> TYPES = Map.of(
      "z80", Libspectrum.Z80, "sna", Libspectrum.SNA, "sp", Libspectrum.SP, "szx", Libspectrum.SZX);

  /**
   * The formats libspectrum is asked about. Not SP: its reader copies the memory to the address the
   * file names without taking away the 0x4000 its buffer starts at, so a 48K SP writes 16K past the
   * end of the buffer and the process dies (malloc(): corrupted top size), and a 16K one lands a
   * page too high. Nothing in libspectrum's own tests reads an SP.
   */
  private static final java.util.Set<String> ASKED = java.util.Set.of("z80", "sna", "szx");

  private Reference() {
  }

  static boolean present() {
    try {
      return Libspectrum.INSTANCE != null;
    } catch (Throwable notInstalled) {
      return false;
    }
  }

  static boolean asks(String format) {
    return ASKED.contains(format);
  }

  /** The fields libspectrum reads from that image, or why it would not. */
  static Fields read(byte[] image, String format) {
    if (!asks(format)) {
      throw new IllegalArgumentException("libspectrum is not asked about " + format);
    }
    return withSnap(image, format, Fields::ofReference).orElse(Fields.refused("libspectrum refuses it"));
  }

  /** That image as libspectrum writes it in another format, if it reads it and can write it. */
  static Optional<byte[]> convert(byte[] image, String from, String to) {
    return withSnap(image, from, snap -> {
      PointerByReference buffer = new PointerByReference();
      LongByReference length = new LongByReference();
      IntByReference lost = new IntByReference();
      if (Libspectrum.INSTANCE.libspectrum_snap_write(buffer, length, lost, snap, TYPES.get(to), null, 0) != 0) {
        return null;
      }
      byte[] written = buffer.getValue().getByteArray(0, (int) length.getValue());
      Libspectrum.INSTANCE.libspectrum_free(buffer.getValue());
      return written;
    });
  }

  private static <T> Optional<T> withSnap(byte[] image, String format, java.util.function.Function<Pointer, T> use) {
    Libspectrum lib = Libspectrum.INSTANCE;
    lib.libspectrum_init();
    Pointer snap = lib.libspectrum_snap_alloc();
    try {
      if (lib.libspectrum_snap_read(snap, image, new NativeLong(image.length), TYPES.get(format), null) != 0) {
        return Optional.empty();
      }
      return Optional.ofNullable(use.apply(snap));
    } finally {
      lib.libspectrum_snap_free(snap);
    }
  }
}
