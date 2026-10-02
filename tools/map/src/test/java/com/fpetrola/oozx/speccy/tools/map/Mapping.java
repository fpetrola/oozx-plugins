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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A recorded game mapped as the window maps one, twice over: first finding the room and the
 * player in memory as it goes, then again from the start knowing them, which is what a map can
 * do with a recording. Whoever measures watches each leaving and arrival against what they know
 * of the game.
 */
final class Mapping {
  private static final String[] ROW_KEYS = {"CAPS Z X C V", "A S D F G", "Q W E R T", "1 2 3 4 5", "0 9 8 7 6", "P O I U Y", "ENT L K J H", "SPC SYM M N B"};

  /** What one pass over the game left behind. */
  record Pass(RoomVariable roomVariable, PlayerVariable playerVariable, Rooms rooms, int arrivals, int frames) {
  }

  interface Watching {
    default void leaving(Speccy speccy, int frames, PlayerVariable.Side said) {
    }

    /** A room arrived at, and the side the last one was voted left by, if any. */
    default void arrived(Speccy speccy, int frames, Rooms rooms, PlayerVariable player, RoomVariable roomVariable, PlayerVariable.Side leftBy) {
    }

    /** The way into the room arrived at last, told only now. */
    default void wayToldLate(PlayerVariable.Side side) {
    }
  }

  private Mapping() {
  }

  static RzxSession open(File recording) {
    Assumptions.assumeTrue(recording.isFile(), "needs " + recording);
    RzxSession session = RzxSession.open(recording);
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

  /** Both passes, the second knowing what the first found, each watched. */
  static Pass twice(File recording, Watching first, Watching second) {
    RzxSession session = open(recording);
    Pass found = play(session, new RoomVariable(session.getSpeccy().memory::peek), new PlayerVariable(session.getSpeccy().memory::peek), first);
    describe("FINDING OUT", found);
    session = open(recording);
    PlayerVariable knowing = new PlayerVariable(session.getSpeccy().memory::peek);
    knowing.learnedFrom(found.playerVariable);
    Pass known = play(session, new RoomVariable(session.getSpeccy().memory::peek, found.roomVariable.survivors()), knowing, second);
    describe("KNOWING", known);
    return known;
  }

  static Pass play(RzxSession session, RoomVariable roomVariable, PlayerVariable playerVariable, Watching watching) {
    Speccy speccy = session.getSpeccy();
    InputsSeen inputs = new InputsSeen(speccy);
    Rooms rooms = new Rooms();
    int[] frames = {0}, arrivals = {0};
    Mapper mapper = new Mapper(roomVariable, playerVariable, rooms, new Mapper.Listener() {
      @Override
      public void leaving(PlayerVariable.Side said) {
        watching.leaving(speccy, frames[0], said);
      }

      @Override
      public void arrived(PlayerVariable.Side leftBy) {
        arrivals[0]++;
        watching.arrived(speccy, frames[0], rooms, playerVariable, roomVariable, leftBy);
      }

      @Override
      public void wayToldLate(PlayerVariable.Side side) {
        watching.wayToldLate(side);
      }
    });
    try {
      for (; frames[0] < 200_000 && session.playFrame(); frames[0]++) mapper.frame(screenOf(speccy), inputs.frame());
    } finally {
      inputs.off();
      session.release();
    }
    return new Pass(roomVariable, playerVariable, rooms, arrivals[0], frames[0]);
  }

  private static void describe(String label, Pass pass) {
    PlayerVariable.Found player = pass.playerVariable.player();
    System.out.printf("%s: %d frames, %d arrivals, %d rooms on the map, %d of %d voted ways not drawn as voted; room identity of %d bytes; player %s%n",
        label, pass.frames, pass.arrivals, pass.rooms.rooms().size(), undrawn(pass.rooms), votedWays(pass.rooms), pass.roomVariable.survivors().size(),
        player == null ? "not found" : String.format("0x%04X %s, up %s down %s", player.address(), pass.playerVariable.wide() ? "16 bits" : "8 bits", keyName(player.up()), keyName(player.down())));
    System.out.println("  " + pass.playerVariable.state());
  }

  static int votedWays(Rooms rooms) {
    int ways = 0;
    for (Rooms.Room from : rooms.rooms()) for (Rooms.Room to : rooms.rooms()) if (from.number < to.number && rooms.sideOf(from, to) != null) ways++;
    return ways;
  }

  /** The voted ways whose rooms are not drawn side by side as voted. */
  static int undrawn(Rooms rooms) {
    int undrawn = 0;
    for (Rooms.Room from : rooms.rooms()) {
      for (Rooms.Room to : rooms.rooms()) {
        PlayerVariable.Side side = from.number < to.number ? rooms.sideOf(from, to) : null;
        if (side != null && (to.x != from.x + Rooms.dx(side) || to.y != from.y + Rooms.dy(side))) undrawn++;
      }
    }
    return undrawn;
  }

  static String keyName(int key) {
    if (key >= InputsSeen.KEMPSTON) return "Kempston bit " + (key - InputsSeen.KEMPSTON);
    return ROW_KEYS[key / 5].split(" ")[key % 5];
  }

  /** Every room drawn on its square, labelled. */
  static void draw(Map<Integer, int[]> where, Map<Integer, byte[]> screens, IntFunction<String> label, File into) throws Exception {
    int minX = where.values().stream().mapToInt(at -> at[0]).min().orElse(0), minY = where.values().stream().mapToInt(at -> at[1]).min().orElse(0);
    int maxX = where.values().stream().mapToInt(at -> at[0]).max().orElse(0), maxY = where.values().stream().mapToInt(at -> at[1]).max().orElse(0);
    BufferedImage map = new BufferedImage((maxX - minX + 1) * 256, (maxY - minY + 1) * 192, BufferedImage.TYPE_INT_RGB);
    Graphics2D pen = map.createGraphics();
    pen.setColor(Color.DARK_GRAY);
    pen.fillRect(0, 0, map.getWidth(), map.getHeight());
    where.forEach((room, at) -> {
      int x = (at[0] - minX) * 256, y = (at[1] - minY) * 192;
      byte[] screen = screens.get(room);
      if (screen != null) pen.drawImage(Screens.picture(screen, null), x, y, null);
      pen.setColor(Color.YELLOW);
      pen.drawString(label.apply(room), x + 4, y + 14);
    });
    pen.dispose();
    into.getParentFile().mkdirs();
    javax.imageio.ImageIO.write(map, "png", into);
  }

  /** The map as it stands, each room by its own number. */
  static void draw(Rooms rooms, File into) throws Exception {
    Map<Integer, int[]> where = new HashMap<>();
    Map<Integer, byte[]> screens = new HashMap<>();
    for (Rooms.Room room : rooms.rooms()) {
      where.put(room.number, new int[]{room.x, room.y});
      screens.put(room.number, room.screen);
    }
    draw(where, screens, String::valueOf, into);
  }
}
