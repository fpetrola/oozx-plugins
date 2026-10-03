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
import com.fpetrola.oozx.speccy.devices.MachineFrame;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Timer;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** The map of the game being played, drawn as the rooms come: each on its square, the one in play outlined. */
public class MapFrame extends MachineFrame {
  private static final int REFRESH_MILLIS = 250;
  private final JLabel said = new JLabel(" ");
  private final Drawing drawing = new Drawing();
  private volatile LiveMap live;

  public MapFrame() {
    super("Map");
    setSize(480, 360);
    JButton again = new JButton("Start again");
    again.addActionListener(e -> startAgain(machine()));
    controls.add(again);
    controls.add(said);
    assemble(drawing);
    setCompact(false);
    Timer refresh = new Timer(REFRESH_MILLIS, e -> {
      LiveMap live = this.live;
      if (live == null) said.setText("not clipped onto a machine");
      else {
        Rooms shown = live.shown();
        int found = shown.rooms().size();
        String way = shown.lastWayOut();
        Twin twin = live.twin();
        said.setText(found + (found == 1 ? " room" : " rooms") + (way.isEmpty() ? "" : " - last way out: " + way)
            + (live.playerKnown() ? "" : " - looking for the player")
            + (twin == null || twin.caughtUp() ? "" : " - mapping again from the start, " + twin.frame() + " of " + (twin.frame() + twin.behind()) + " frames"));
      }
      drawing.repaint();
    });
    refresh.start();
    addInternalFrameListener(new InternalFrameAdapter() {
      @Override
      public void internalFrameClosed(InternalFrameEvent e) {
        refresh.stop();
        startAgain(null);
      }
    });
  }

  @Override
  protected void machineChanged(Speccy was, Speccy now) {
    startAgain(now);
  }

  /** What is known starts over: the rooms, what the game's memory says of rooms and the player, and the recording the map is made again from. */
  private void startAgain(Speccy machine) {
    if (live != null) live.stop();
    live = machine == null ? null : new LiveMap(machine, new LiveMap.Twins() {
    });
  }

  @Override
  protected String expandTip() {
    return "Show the map, or just the buttons";
  }

  @Override
  protected String attachTip() {
    return "Clip this onto the machine whose game is being mapped";
  }

  /** The whole map fitted to the window, and from there dragged about and zoomed with the wheel; a double click fits it again. */
  private class Drawing extends JComponent {
    /** By the screen itself: a room seen again has a new one, and the old picture goes with the old screen. */
    private final Map<byte[], BufferedImage> pictures = new java.util.IdentityHashMap<>();
    private double zoom = 1, panX, panY;

    Drawing() {
      MouseAdapter mouse = new MouseAdapter() {
        private int fromX, fromY;

        @Override
        public void mousePressed(MouseEvent e) {
          fromX = e.getX();
          fromY = e.getY();
          if (e.getClickCount() == 2) {
            zoom = 1;
            panX = panY = 0;
            repaint();
          }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
          panX += e.getX() - fromX;
          panY += e.getY() - fromY;
          fromX = e.getX();
          fromY = e.getY();
          repaint();
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          double by = e.getWheelRotation() < 0 ? 1.2 : 1 / 1.2;
          zoom *= by;
          panX = e.getX() - (e.getX() - panX) * by;
          panY = e.getY() - (e.getY() - panY) * by;
          repaint();
        }
      };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
      addMouseWheelListener(mouse);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D pen = (Graphics2D) g;
      pen.setColor(Color.DARK_GRAY);
      pen.fillRect(0, 0, getWidth(), getHeight());
      LiveMap live = MapFrame.this.live;
      if (live == null) return;
      Rooms shown = live.shown();
      List<Rooms.Room> found = shown.rooms();
      if (found.isEmpty()) return;
      int left = found.stream().mapToInt(room -> room.x).min().orElse(0);
      int top = found.stream().mapToInt(room -> room.y).min().orElse(0);
      int across = found.stream().mapToInt(room -> room.x).max().orElse(0) - left + 1;
      int down = found.stream().mapToInt(room -> room.y).max().orElse(0) - top + 1;
      double scale = Math.min(getWidth() / (across * 256.0), getHeight() / (down * 192.0));
      int width = (int) (256 * scale), height = (int) (192 * scale);
      pen.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      pen.translate(panX, panY);
      pen.scale(zoom, zoom);
      Rooms.Room current = shown.current();
      for (Rooms.Room room : found) {
        int x = (room.x - left) * width, y = (room.y - top) * height;
        pen.drawImage(pictures.computeIfAbsent(room.screen, this::pictureOf), x, y, width, height, null);
        if (room == current) {
          pen.setColor(Color.YELLOW);
          pen.drawRect(x, y, width - 1, height - 1);
          pen.drawRect(x + 1, y + 1, width - 3, height - 3);
        }
      }
    }

    private BufferedImage pictureOf(byte[] screen) {
      return Screens.picture(screen, machine() == null ? null : machine().display.picture().palette);
    }
  }
}
