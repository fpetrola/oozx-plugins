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

import com.fpetrola.oozx.rzx.RzxSession;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * What the window does while the game is played, done to a recording standing in for the player:
 * the window's own live map, its twin stepped here - all it can, so its map can be graded against
 * the game's own screen number as the second pass over the recording is; or as much as a thread of
 * its own manages against a game at fifty frames a second, to see what the window shows meanwhile.
 */
class AMapMadeAgainWhileThePlayerPlaysTest {
  private static final int SAMPLE = 1000, PICTURE = 5000, FRAME_RATE = 50;
  private static final File PICTURES = new File("target/map-pictures");

  @Test
  @Timeout(value = 25, unit = TimeUnit.MINUTES)
  void theTwinsMapIsTheSecondPassesMap() throws Exception {
    DynamiteDanIsMappedTest.Grading[] grading = {null};
    Window window = new Window("dynamitedan", DynamiteDanIsMappedTest.RECORDING, 200_000, false) {
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

  /** What the window shows as each game is played, sampled and drawn into target/map-pictures, for looking at. */
  @Test
  @Timeout(value = 90, unit = TimeUnit.MINUTES)
  void whatTheWindowShows() throws Exception {
    for (Window window : List.of(
        new Window("dizzy", DizzyIsMappedTest.RECORDING, 200_000, true),
        new Window("jsw", JetSetWilly.RECORDING, 60_000, true))) {
      window.play();
      window.map.stop();
    }
  }

  /** The window's live map over a recording, the twin stepped here: all it can, or what a thread of its own would manage. */
  private static class Window implements LiveMap.Twins {
    final String name;
    final File recording;
    final int atMost;
    final boolean paced;
    final int[] startedAt = new int[8];
    int starts, frames, twinFrames;
    long twinNanos;
    LiveMap map;

    Window(String name, File recording, int atMost, boolean paced) {
      this.name = name;
      this.recording = recording;
      this.atMost = atMost;
      this.paced = paced;
    }

    /** The twin's arrivals, traced with the state of the player it was taught, to see why a way is or is not told. */
    @Override
    public Mapper.Listener listener() {
      return new Mapper.Listener() {
        int traced;

        @Override
        public void leaving(PlayerVariable.Side said) {
          if (traced < 60) System.out.printf("%s twin #%d frame %d: leaving, side %s%n", name, starts, map.twin().frame(), said);
        }

        @Override
        public void arrived(PlayerVariable.Side leftBy) {
          Twin twin = map.twin();
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
      System.out.println(sample());
      picture(palette);
    }

    /** Twin frames for this live frame: as many as its own thread would manage at the speed it has shown, against a game at fifty a second. */
    private int stepsAllowed() {
      if (!paced) return Integer.MAX_VALUE;
      if (twinFrames < 100) return 4;
      return Math.max(1, (int) (twinFrames / (twinNanos / 1e9) / FRAME_RATE));
    }

    String sample() {
      Twin twin = map.twin();
      PlayerVariable player = map.playerVariable;
      String shows = map.showsATwin() ? "a twin" : "the first pass";
      List<Integer> identity = map.roomVariable.survivors();
      return String.format("%s frame %6d: shows %s map, %d rooms; first pass %d rooms, identity %d bytes%s, player %s, taught %s; twin %s",
          name, frames, shows, map.shown().rooms().size(), map.rooms.rooms().size(), identity.size(),
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
