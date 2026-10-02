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

package com.fpetrola.oozx.speccy.tools.map;

/** The shape of a Spectrum screen, and two screens compared by it. */
final class Screens {
  static final int SCREEN = 6912, BITMAP = 6144, COLUMNS = 32, ROWS = 24, CELLS = COLUMNS * ROWS;

  private Screens() {
  }

  /** The character cell a bitmap byte belongs to. */
  static int cellOf(int offset) {
    return ((offset >> 8 & 0x18) | (offset >> 5 & 7)) * COLUMNS + (offset & 31);
  }

  /** The eight bitmap bytes of a cell, top line first. */
  static int lineOffset(int cell, int line) {
    int x = cell % COLUMNS, y = cell / COLUMNS;
    return (y & 0x18) << 8 | line << 8 | (y & 7) << 5 | x;
  }

  /** How much of the bitmap differs between two screens, as a fraction. */
  static double changed(byte[] one, byte[] other) {
    int differing = 0;
    for (int offset = 0; offset < BITMAP; offset++) if (one[offset] != other[offset]) differing++;
    return differing / (double) BITMAP;
  }

  /** The cells that differ between two screens, in their pixels or their colours. */
  static int differingCells(byte[] one, byte[] other) {
    int differing = 0;
    for (int cell = 0; cell < CELLS; cell++) {
      boolean differs = one[BITMAP + cell] != other[BITMAP + cell];
      for (int line = 0; line < 8 && !differs; line++) {
        int offset = lineOffset(cell, line);
        differs = one[offset] != other[offset];
      }
      if (differs) differing++;
    }
    return differing;
  }
}
