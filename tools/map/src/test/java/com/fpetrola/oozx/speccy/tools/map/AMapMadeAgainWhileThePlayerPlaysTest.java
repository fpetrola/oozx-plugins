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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Timeout;

/**
 * What the window does while the game is played, done to a recording standing in for the player:
 * the window's own live map, its twin stepped here as much as a thread of its own manages against
 * a game at fifty frames a second, so what the window shows meanwhile is seen as well as graded.
 */
class AMapMadeAgainWhileThePlayerPlaysTest {
  private static final int SAMPLE = 1000, PICTURE = 5000, FRAME_RATE = 50;

  /** What a game tells of itself, when it does: the room it is in, where it puts each, and which side one lies on from another. */
  interface Truth {
    int room(Speccy speccy);

    Map<Integer, int[]> where(Speccy speccy, Map<Integer, byte[]> screens);

    /** Columns the map wraps round after, on a cylinder; 0 on a plane. */
    int wrapX();

    /** LEFT, RIGHT, UP, DOWN, or none. */
    String side(Speccy speccy, int from, int to);
  }

  static final Truth JET_SET_WILLY = new Truth() {
    public int room(Speccy speccy) {
      return JetSetWilly.room(speccy);
    }

    public Map<Integer, int[]> where(Speccy speccy, Map<Integer, byte[]> screens) {
      return JetSetWilly.reference(screens);
    }

    public int wrapX() {
      return 0;
    }

    public String side(Speccy speccy, int from, int to) {
      return JetSetWilly.sideFrom(speccy, from, to);
    }
  };

  static final Truth DYNAMITE_DAN = new Truth() {
    public int room(Speccy speccy) {
      return speccy.memory.peek(DynamiteDanIsMappedTest.SCREEN) & 0xff;
    }

    public Map<Integer, int[]> where(Speccy speccy, Map<Integer, byte[]> screens) {
      Map<Integer, int[]> cells = new HashMap<>();
      for (int screen = 0; screen < 48; screen++) cells.put(screen, DynamiteDanIsMappedTest.cellOf(screen));
      return cells;
    }

    public int wrapX() {
      return DynamiteDanIsMappedTest.ACROSS;
    }

    public String side(Speccy speccy, int from, int to) {
      PlayerVariable.Side side = DynamiteDanIsMappedTest.sideFrom(from, to);
      return side == null ? "none" : side.toString();
    }
  };
  private static final File PICTURES = new File("target/map-pictures");

  @Test
  @Timeout(value = 25, unit = TimeUnit.MINUTES)
  void theTwinsMapIsTheSecondPassesMap() throws Exception {
    DynamiteDanIsMappedTest.Grading[] grading = {null};
    Window window = new Window("dynamitedan", DynamiteDanIsMappedTest.RECORDING, 200_000, DYNAMITE_DAN) {
      @Override
      public Mapper.Listener listener() {
        grading[0] = new DynamiteDanIsMappedTest.Grading();
        return new Mapper.Listener() {
          @Override
          public void arrived(PlayerVariable.Side leftBy) {
            Twin twin = map.twin();
            grading[0].arrived(twin.machine(), twin.frame(), twin.rooms, twin.playerVariable, twin.roomVariable, leftBy);
          }

          @Override
          public void wayToldLate(PlayerVariable.Side side) {
            grading[0].wayToldLate(side);
          }
        };
      }
    };
    window.play();
    Twin twin = window.map.twin();
    System.out.printf("TWIN: started %d times, at frames %s; followed to frame %d of %d, %d behind%n", window.starts, java.util.Arrays.toString(window.startedAt), twin.frame(), window.frames, twin.behind());
    grading[0].report(twin.rooms);
    window.map.stop();
  }

  /** What the window shows as each game is played, sampled and drawn into target/map-pictures, for looking at: run with -Dmap.look=true, it takes an hour. */
  @Test
  @EnabledIfSystemProperty(named = "map.look", matches = "true")
  @Timeout(value = 120, unit = TimeUnit.MINUTES)
  void whatTheWindowShows() throws Exception {
    for (Window window : List.of(
        new Window("dizzy", DizzyIsMappedTest.RECORDING, 200_000, null),
        new Window("jsw", JetSetWilly.RECORDING, 200_000, JET_SET_WILLY))) {
      window.play();
      window.map.stop();
    }
  }

  /** The window's live map over a recording, the twin stepped here: all it can, or what a thread of its own would manage. */
  private static class Window implements LiveMap.Twins {
    final String name;
    final File recording;
    final int atMost;
    /** What the game tells of itself, or null. */
    final Truth truth;
    /** (our room number, the game's) at every arrival, per twin map, so that whichever map is shown can be held to the game. */
    final Map<Rooms, List<int[]>> seenAsOf = new HashMap<>();
    List<int[]> seenAs = new ArrayList<>();
    final Map<Integer, byte[]> screens = new HashMap<>();
    int right, wrong, untold;
    private Map<Rooms.Room, int[]> were = new HashMap<>();
    private Rooms wereOf;
    /** Where the map shown put each of the game's rooms, to hold the next map shown against. */
    private Map<Integer, int[]> placementShown;
    final int[] startedAt = new int[8];
    int starts, frames, twinFrames;
    long twinNanos;
    LiveMap map;

    Window(String name, File recording, int atMost, Truth truth) {
      this.name = name;
      this.recording = recording;
      this.atMost = atMost;
      this.truth = truth;
    }

    /** The twin's arrivals, traced with the state of the player it was taught, to see why a way is or is not told. */
    @Override
    public Mapper.Listener listener() {
      if (starts > 0) System.out.println(waysHeld());
      seenAs = new ArrayList<>();
      right = wrong = untold = 0;
      return new Mapper.Listener() {
        int traced, roomBefore = -1;

        @Override
        public void leaving(PlayerVariable.Side said) {
          if (traced < 60) System.out.printf("%s twin #%d frame %d: leaving, side %s%n", name, starts, map.twin().frame(), said);
        }

        @Override
        public void arrived(PlayerVariable.Side leftBy) {
          Twin twin = map.twin();
          if (truth != null) {
            int room = truth.room(twin.machine());
            seenAs.add(new int[]{twin.rooms.current().number, room});
            screens.putIfAbsent(room, Screens.of(twin.machine().memory::peek));
            if (roomBefore >= 0 && roomBefore != room) {
              String said = truth.side(twin.machine(), roomBefore, room);
              if (leftBy == null) untold++;
              else if (said.equals(leftBy.toString())) right++;
              else {
                wrong++;
                System.out.printf("%s twin #%d frame %d: voted %s where the game says %s, %d -> %d; %s%n", name, starts, twin.frame(), leftBy, said, roomBefore, room, twin.playerVariable.state());
              }
            }
            roomBefore = room;
          }
          if (traced++ < 60) System.out.printf("%s twin #%d frame %d: arrived, left by %s; %d rooms; %s%n", name, starts, twin.frame(), leftBy, twin.rooms.rooms().size(), twin.playerVariable.state());
        }

        @Override
        public void wayToldLate(PlayerVariable.Side side) {
          if (traced < 60) System.out.printf("%s twin #%d frame %d: way told late, %s%n", name, starts, map.twin().frame(), side);
        }
      };
    }

    @Override
    public void started(Twin twin) {
      seenAsOf.put(twin.rooms, seenAs);
      if (starts < startedAt.length) startedAt[starts] = frames;
      starts++;
      System.out.printf("%s frame %d: twin #%d started, taught %s%n", name, frames, starts, map.playerVariable.taught());
    }

    void play() throws Exception {
      RzxSession live = Mapping.open(recording);
      map = new LiveMap(live.getSpeccy(), this);
      int[] palette = live.getSpeccy().display.picture().palette;
      try {
        for (; frames < atMost && live.playFrame(); frames++) {
          Twin twin = map.twin();
          for (int step = 0; twin != null && step < stepsAllowed(); step++) {
            long from = System.nanoTime();
            boolean stepped = twin.step();
            twinNanos += System.nanoTime() - from;
            if (!stepped) break;
            twinFrames++;
          }
          if (frames % SAMPLE == 0) System.out.println(sample());
          if (frames % PICTURE == 0) picture(palette);
        }
      } finally {
        live.release();
      }
      for (Twin twin = map.twin(); twin != null && twin.step(); ) ;
      System.out.println(sample());
      picture(palette);
      Rooms shown = map.shown();
      if (truth != null && !seenAsOf.getOrDefault(shown, List.of()).isEmpty()) {
        System.out.println(waysHeld());
        Map<Integer, Integer> gameRoomOf = Mapping.gameRoomOf(shown, seenAsOf.get(shown));
        System.out.printf("%s map shown at the end: %d rooms, %d of them a second room for one of the game's; %s%n", name, shown.rooms().size(),
            gameRoomOf.size() - new java.util.HashSet<>(gameRoomOf.values()).size(), Mapping.placement(shown, gameRoomOf, truth.where(map.twin().machine(), screens), truth.wrapX()));
      }
    }

    private String waysHeld() {
      return String.format("%s twin #%d: ways voted right %d, wrong %d, not told %d", name, starts, right, wrong, untold);
    }

    /** Rooms of the shown map not where they were at the last sample, a shift of the whole map aside. */
    private int movedSince(Rooms shown) {
      Map<Rooms.Room, int[]> now = new HashMap<>();
      for (Rooms.Room room : shown.rooms()) now.put(room, new int[]{room.x, room.y});
      int moved = 0;
      if (shown == wereOf) {
        Map<String, Integer> shifts = new HashMap<>();
        now.forEach((room, at) -> { if (were.containsKey(room)) shifts.merge(shiftOf(room, at), 1, Integer::sum); });
        String common = shifts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("0,0");
        for (Map.Entry<Rooms.Room, int[]> entry : now.entrySet()) if (were.containsKey(entry.getKey()) && !shiftOf(entry.getKey(), entry.getValue()).equals(common)) moved++;
      }
      were = now;
      wereOf = shown;
      return moved;
    }

    /** The game's rooms on both maps that are not where the other map has them, a shift of the whole map aside. */
    private static int placedDifferently(Map<Integer, int[]> before, Map<Integer, int[]> now) {
      Map<String, Integer> shifts = new HashMap<>();
      now.forEach((room, at) -> { if (before.containsKey(room)) shifts.merge((at[0] - before.get(room)[0]) + "," + (at[1] - before.get(room)[1]), 1, Integer::sum); });
      String common = shifts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("0,0");
      int differently = 0;
      for (Map.Entry<Integer, int[]> entry : now.entrySet()) {
        int[] was = before.get(entry.getKey());
        if (was != null && !((entry.getValue()[0] - was[0]) + "," + (entry.getValue()[1] - was[1])).equals(common)) differently++;
      }
      return differently;
    }

    private String shiftOf(Rooms.Room room, int[] at) {
      int[] was = were.get(room);
      return (at[0] - was[0]) + "," + (at[1] - was[1]);
    }

    /** Twin frames for this live frame: as many as its own thread would manage at the speed it has shown, against a game at fifty a second. */
    private int stepsAllowed() {
      if (twinFrames < 100) return 4;
      return Math.max(1, (int) (twinFrames / (twinNanos / 1e9) / FRAME_RATE));
    }

    String sample() {
      Twin twin = map.twin();
      PlayerVariable player = map.playerVariable;
      String shows = map.showsATwin() ? "a twin" : "the first pass";
      List<Integer> identity = map.roomVariable.survivors();
      Rooms shown = map.shown();
      String swapped = "";
      if (truth != null && map.showsATwin()) {
        Map<Integer, int[]> placement = new HashMap<>();
        Map<Integer, Integer> gameRoomOf = Mapping.gameRoomOf(shown, seenAsOf.getOrDefault(shown, List.of()));
        for (Rooms.Room room : shown.rooms()) if (gameRoomOf.containsKey(room.number)) placement.put(gameRoomOf.get(room.number), new int[]{room.x, room.y});
        if (shown != wereOf && placementShown != null) swapped = String.format(", %d of the game's rooms placed differently than on the map shown before", placedDifferently(placementShown, placement));
        placementShown = placement;
      }
      return String.format("%s frame %6d: shows %s map, %d rooms, %d moved%s, %d of %d voted ways not drawn as voted; first pass %d rooms, identity %d bytes%s, player %s, taught %s; twin %s",
          name, frames, shows, shown.rooms().size(), movedSince(shown), swapped, Mapping.undrawn(shown), Mapping.votedWays(shown), map.rooms.rooms().size(), identity.size(),
          identity.size() <= 24 ? identity.stream().map(address -> String.format(" %04X", address)).reduce("", String::concat) : "",
          !player.known() ? "not found" : player.trusted() ? "trusted" : "found", player.taught(),
          twin == null ? "none" : String.format("#%d %d rooms, identity %d bytes, %d behind, %.0f frames/s", starts, twin.rooms.rooms().size(), twin.roomVariable.survivors().size(), twin.behind(), twinFrames / (twinNanos / 1e9)));
    }

    void picture(int[] palette) throws IOException {
      BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
      Graphics2D pen = image.createGraphics();
      pen.setColor(Color.DARK_GRAY);
      pen.fillRect(0, 0, image.getWidth(), image.getHeight());
      Screens.draw(pen, map.shown(), image.getWidth(), image.getHeight(), screen -> Screens.picture(screen, palette));
      pen.dispose();
      PICTURES.mkdirs();
      ImageIO.write(image, "png", new File(PICTURES, String.format("%s-%06d.png", name, frames)));
    }
  }
}
