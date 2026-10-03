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

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.rzx.RzxSession;
import org.junit.jupiter.api.Assumptions;

import java.io.File;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Deque;
import java.util.ArrayDeque;

/**
 * A whole game of Jet Set Willy recorded, and what the game itself keeps: the room it is in at
 * 0x8420, Willy as an address in its attribute buffer at 0x85D3, and each room's neighbours at
 * 0xE9 to 0xEC of its 256 bytes from 0xC000. The truth a map worked out from the screen is held to.
 */
final class JetSetWilly {
  static final File RECORDING = new File(System.getenv("HOME"), "detodo/spectrum/jsw/jsw-full.rzx");
  static final int ROOM = 0x8420, WILLY = 0x85D3, ATTRIBUTES = 0x5C00, ROOMS = 0xC000, EXITS = 0xE9;
  static final File REFERENCE = new File(System.getenv("HOME"), "detodo/spectrum/maps/JetSetWilly1.png");
  /** The reference's tiles: the play area with the name line the game prints beneath it, in rows every 136 pixels from 136 down and one above. */
  static final int TILE_WIDTH = 256, TILE_PITCH = 136, FIRST_ROW = 136, NAME_LINE = 128, NAME_LINES = 8;
  static final String[] SIDES = {"LEFT", "RIGHT", "UP", "DOWN"};

  private JetSetWilly() {
  }

  static RzxSession open() {
    Assumptions.assumeTrue(RECORDING.isFile(), "needs " + RECORDING);
    RzxSession session = RzxSession.open(RECORDING);
    Speccy speccy = session.getSpeccy();
    speccy.picture.active = false;
    speccy.sound.output.enabled = false;
    speccy.sound.setCard(new com.fpetrola.oozx.speccy.modules.sound.SilentSoundDevice());
    return session;
  }

  static byte[] screenOf(Speccy speccy) {
    byte[] screen = new byte[Screens.SCREEN];
    for (int offset = 0; offset < screen.length; offset++) screen[offset] = (byte) speccy.memory.peek(0x4000 + offset);
    return screen;
  }

  static int room(Speccy speccy) {
    return speccy.memory.peek(ROOM) & 0xff;
  }

  /** Willy's cell, as column and row, from the attribute address the game keeps him at. */
  static int[] willy(Speccy speccy) {
    int at = (speccy.memory.peek(WILLY) & 0xff) | (speccy.memory.peek(WILLY + 1) & 0xff) << 8;
    return new int[]{at & 31, (at - ATTRIBUTES) >> 5};
  }

  /** The game's own table of ways laid out from the bathroom, each room beside the one it is reached from, as a map is. */
  static Map<Integer, int[]> table(Speccy speccy) {
    Map<Integer, int[]> table = new HashMap<>();
    Deque<Integer> toVisit = new ArrayDeque<>(List.of(33));
    table.put(33, new int[]{0, 0});
    while (!toVisit.isEmpty()) {
      int room = toVisit.pop();
      for (int side = 0; side < 4; side++) {
        int next = speccy.memory.peek(ROOMS + room * 256 + EXITS + side) & 0xff;
        if (next == room || next >= 61 || table.containsKey(next)) continue;
        table.put(next, new int[]{table.get(room)[0] + (side == 1 ? 1 : side == 0 ? -1 : 0), table.get(room)[1] + (side == 3 ? 1 : side == 2 ? -1 : 0)});
        toVisit.add(next);
      }
    }
    return table;
  }

  /**
   * Where the map at maps.speccy.cz puts each room, as a grid of its tiles: a room is known by the
   * name line along the bottom of its screen, which the game draws in the bitmap as well.
   */
  static Map<Integer, int[]> reference(Map<Integer, byte[]> screens) {
    BufferedImage map;
    try {
      map = ImageIO.read(REFERENCE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    List<int[]> tiles = new ArrayList<>();
    List<boolean[]> bands = new ArrayList<>();
    for (int row = -1; FIRST_ROW + TILE_PITCH * row + NAME_LINE + NAME_LINES <= map.getHeight(); row++) {
      int top = FIRST_ROW + TILE_PITCH * row + NAME_LINE;
      if (top < 0) continue;
      for (int column = 0; (column + 1) * TILE_WIDTH <= map.getWidth(); column++) {
        boolean[] band = new boolean[TILE_WIDTH * NAME_LINES];
        int lit = 0;
        for (int y = 0; y < NAME_LINES; y++) {
          for (int x = 0; x < TILE_WIDTH; x++) {
            int rgb = map.getRGB(column * TILE_WIDTH + x, top + y);
            if (band[y * TILE_WIDTH + x] = (rgb >> 16 & 0xff) + (rgb >> 8 & 0xff) + (rgb & 0xff) > 96) lit++;
          }
        }
        if (lit > 0) {
          tiles.add(new int[]{column, row});
          bands.add(band);
        }
      }
    }
    Map<Integer, int[]> where = new HashMap<>();
    screens.forEach((room, screen) -> {
      boolean[] ours = new boolean[TILE_WIDTH * NAME_LINES];
      for (int y = 0; y < NAME_LINES; y++) {
        int line = NAME_LINE + y;
        for (int column = 0; column < 32; column++) {
          int offset = (line & 0xC0) << 5 | (line & 7) << 8 | (line & 0x38) << 2 | column;
          for (int bit = 0; bit < 8; bit++) ours[y * TILE_WIDTH + column * 8 + bit] = (screen[offset] << bit & 0x80) != 0;
        }
      }
      int best = -1;
      double closest = 0;
      for (int tile = 0; tile < tiles.size(); tile++) {
        boolean[] band = bands.get(tile);
        int both = 0, either = 0, bothInverted = 0, eitherInverted = 0;
        for (int i = 0; i < ours.length; i++) {
          if (ours[i] && band[i]) both++;
          if (ours[i] || band[i]) either++;
          if (ours[i] && !band[i]) bothInverted++;
          if (ours[i] || !band[i]) eitherInverted++;
        }
        double alike = Math.max(either == 0 ? 0 : both / (double) either, eitherInverted == 0 ? 0 : bothInverted / (double) eitherInverted);
        if (alike > closest) {
          closest = alike;
          best = tile;
        }
      }
      if (closest > 0.5) where.put(room, tiles.get(best));
      else System.out.printf("  room %d not found on the reference map, nearest tile %s shares %.2f of its name line%n", room, best < 0 ? "none" : tiles.get(best)[0] + "," + tiles.get(best)[1], closest);
    });
    return where;
  }

  static String sideFrom(Speccy speccy, int from, int to) {
    for (int side = 0; side < 4; side++) {
      if ((speccy.memory.peek(ROOMS + from * 256 + EXITS + side) & 0xff) == to) return SIDES[side];
    }
    return "none";
  }
}
