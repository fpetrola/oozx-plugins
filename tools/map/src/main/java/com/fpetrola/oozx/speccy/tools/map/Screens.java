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

import java.awt.image.BufferedImage;

/** The shape of a Spectrum screen, two screens compared by it, and one drawn. */
final class Screens {
  static final int SCREEN = 6912, BITMAP = 6144, COLUMNS = 32, ROWS = 24, CELLS = COLUMNS * ROWS;

  private Screens() {
  }

  /** The screen as the memory holds it now. */
  static byte[] of(RoomVariable.Ram ram) {
    byte[] screen = new byte[SCREEN];
    for (int offset = 0; offset < screen.length; offset++) screen[offset] = (byte) ram.peek(0x4000 + offset);
    return screen;
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

  /** A Spectrum screen as a picture, in the colours the machine shows it in, or the usual ones without a machine. */
  static BufferedImage picture(byte[] screen, int[] palette) {
    BufferedImage picture = new BufferedImage(256, 192, BufferedImage.TYPE_INT_RGB);
    for (int offset = 0; offset < BITMAP; offset++) {
      int column = offset & 31, line = (offset >> 8 & 7) | (offset >> 2 & 0x38) | (offset >> 5 & 0xc0);
      int attribute = screen[BITMAP + (line >> 3) * 32 + column] & 0xff;
      int bright = attribute >> 3 & 8;
      int ink = colour(palette, attribute & 7 | bright), paper = colour(palette, attribute >> 3 & 7 | bright);
      for (int bit = 0; bit < 8; bit++) {
        picture.setRGB(column * 8 + bit, line, (screen[offset] << bit & 0x80) != 0 ? ink : paper);
      }
    }
    return picture;
  }

  private static int colour(int[] palette, int index) {
    if (palette != null) return palette[index];
    int level = (index & 8) != 0 ? 0xff : 0xcd;
    return ((index & 2) != 0 ? level << 16 : 0) | ((index & 4) != 0 ? level << 8 : 0) | ((index & 1) != 0 ? level : 0);
  }
}
