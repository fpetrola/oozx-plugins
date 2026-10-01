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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomsTest {

  private final Rooms rooms = new Rooms();

  @Test
  void goingOutOnTheRightAndBackOnTheLeftIsTwoRoomsSideBySide() {
    byte[] first = room(1), second = room(2);
    stand(first, 12);
    walk(first, 30);
    drawOver(first, second);
    stand(second, 12);
    walk(second, 1);
    drawOver(second, first);
    stand(first, 12);

    List<Rooms.Room> found = rooms.rooms();
    assertEquals(2, found.size(), "the first room again is not a third");
    assertEquals(0, found.get(0).x);
    assertEquals(1, found.get(1).x, "the second is to the right of the first");
    assertEquals(found.get(0).y, found.get(1).y);
    assertTrue(found.get(0).leadsTo.contains(1) && found.get(1).leadsTo.contains(0));
    assertEquals(found.get(0), rooms.current(), "and it knows it is back in the first");
  }

  private static byte[] room(int seed) {
    byte[] screen = new byte[Rooms.SCREEN];
    new Random(seed).nextBytes(screen);
    return screen;
  }

  private void stand(byte[] screen, int frames) {
    for (int frame = 0; frame < frames; frame++) rooms.frame(screen.clone());
  }

  /** Something a few bytes big moving along a line of characters towards a column. */
  private void walk(byte[] screen, int towards) {
    int from = towards > 16 ? 10 : 22;
    for (int column = from; column != towards; column += Integer.signum(towards - from)) {
      byte[] now = screen.clone();
      for (int pixelLine = 0; pixelLine < 8; pixelLine++) now[0x800 + pixelLine * 256 + column] ^= (byte) 0xff;
      rooms.frame(now);
    }
  }

  /** The next room drawn over the last in three frames, a third at a time. */
  private void drawOver(byte[] was, byte[] next) {
    byte[] now = was.clone();
    for (int third = 0; third < 3; third++) {
      System.arraycopy(next, third * 2304, now, third * 2304, 2304);
      rooms.frame(now.clone());
    }
  }
}
