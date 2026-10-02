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

/**
 * A whole game of Jet Set Willy recorded, and what the game itself keeps: the room it is in at
 * 0x8420, Willy as an address in its attribute buffer at 0x85D3, and each room's neighbours at
 * 0xE9 to 0xEC of its 256 bytes from 0xC000. The truth a map worked out from the screen is held to.
 */
final class JetSetWilly {
  static final File RECORDING = new File(System.getenv("HOME"), "detodo/spectrum/jsw/jsw-full.rzx");
  static final int ROOM = 0x8420, WILLY = 0x85D3, ATTRIBUTES = 0x5C00, ROOMS = 0xC000, EXITS = 0xE9;
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

  static String sideFrom(Speccy speccy, int from, int to) {
    for (int side = 0; side < 4; side++) {
      if ((speccy.memory.peek(ROOMS + from * 256 + EXITS + side) & 0xff) == to) return SIDES[side];
    }
    return "none";
  }
}
