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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;

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

  /** The rooms on their squares, fitted to the width and height, the one in play outlined. */
  static void draw(Graphics2D pen, Rooms shown, int width, int height, Function<byte[], BufferedImage> pictures) {
    List<Rooms.Room> found = shown.rooms();
    if (found.isEmpty()) return;
    int left = found.stream().mapToInt(room -> room.x).min().orElse(0);
    int top = found.stream().mapToInt(room -> room.y).min().orElse(0);
    int across = found.stream().mapToInt(room -> room.x).max().orElse(0) - left + 1;
    int down = found.stream().mapToInt(room -> room.y).max().orElse(0) - top + 1;
    double scale = Math.min(width / (across * 256.0), height / (down * 192.0));
    int roomWidth = (int) (256 * scale), roomHeight = (int) (192 * scale);
    pen.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    Rooms.Room current = shown.current();
    for (Rooms.Room room : found) {
      int x = (room.x - left) * roomWidth, y = (room.y - top) * roomHeight;
      pen.drawImage(pictures.apply(room.screen), x, y, roomWidth, roomHeight, null);
      if (room == current) {
        pen.setColor(Color.YELLOW);
        pen.drawRect(x, y, roomWidth - 1, roomHeight - 1);
        pen.drawRect(x + 1, y + 1, roomWidth - 3, roomHeight - 3);
      }
    }
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
