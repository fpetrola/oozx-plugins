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
import com.fpetrola.oozx.speccy.modules.z80.PcTraps;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The map of the game being played, drawn as the rooms come: each on its square, the one in play outlined. */
public class MapFrame extends MachineFrame {
  private static final int REFRESH_MILLIS = 250;
  private static final int SCREEN = 0x4000;

  private final JLabel said = new JLabel(" ");
  private final Drawing drawing = new Drawing();
  private Rooms rooms = new Rooms();
  private PcTraps.Watch watching;
  private long frame = -1;

  public MapFrame() {
    super("Map");
    setSize(480, 360);
    JButton again = new JButton("Start again");
    again.addActionListener(e -> rooms = new Rooms());
    controls.add(again);
    controls.add(said);
    assemble(drawing);
    setCompact(false);
    Timer refresh = new Timer(REFRESH_MILLIS, e -> {
      int found = rooms.rooms().size();
      String way = rooms.lastWayOut();
      said.setText(machine() == null ? "not clipped onto a machine"
          : found + (found == 1 ? " room" : " rooms") + (way.isEmpty() ? "" : " - last way out: " + way));
      drawing.repaint();
    });
    refresh.start();
    addInternalFrameListener(new InternalFrameAdapter() {
      @Override
      public void internalFrameClosed(InternalFrameEvent e) {
        refresh.stop();
        stopWatching();
      }
    });
  }

  @Override
  protected void machineChanged(Speccy was, Speccy now) {
    stopWatching();
    rooms = new Rooms();
    if (now != null) watching = now.cpu.beforeFetch().watch(0x0000, 0xffff, pc -> lookAtTheScreen(now));
  }

  @Override
  protected String expandTip() {
    return "Show the map, or just the buttons";
  }

  @Override
  protected String attachTip() {
    return "Clip this onto the machine whose game is being mapped";
  }

  /** Once a frame, the first time the processor fetches in a new one: the screen as the last left it. */
  private void lookAtTheScreen(Speccy machine) {
    long now = machine.machine.current.frameCount();
    if (now == frame) return;
    frame = now;
    byte[] screen = new byte[Rooms.SCREEN];
    for (int offset = 0; offset < screen.length; offset++) screen[offset] = (byte) machine.memory.peek(SCREEN + offset);
    rooms.frame(screen);
  }

  private void stopWatching() {
    if (watching != null) watching.off();
    watching = null;
  }

  /** The whole map fitted to the window, and from there dragged about and zoomed with the wheel; a double click fits it again. */
  private class Drawing extends JComponent {
    private final Map<Integer, BufferedImage> pictures = new HashMap<>();
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
      List<Rooms.Room> found = rooms.rooms();
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
      Rooms.Room current = rooms.current();
      for (Rooms.Room room : found) {
        int x = (room.x - left) * width, y = (room.y - top) * height;
        pen.drawImage(pictures.computeIfAbsent(room.number, n -> pictureOf(room.screen)), x, y, width, height, null);
        if (room == current) {
          pen.setColor(Color.YELLOW);
          pen.drawRect(x, y, width - 1, height - 1);
          pen.drawRect(x + 1, y + 1, width - 3, height - 3);
        }
      }
    }

    /** A Spectrum screen as a picture, in the colours the machine shows it in. */
    private BufferedImage pictureOf(byte[] screen) {
      int[] palette = machine() == null ? null : machine().display.picture().palette;
      BufferedImage picture = new BufferedImage(256, 192, BufferedImage.TYPE_INT_RGB);
      for (int offset = 0; offset < Rooms.BITMAP; offset++) {
        int column = offset & 31, line = (offset >> 8 & 7) | (offset >> 2 & 0x38) | (offset >> 5 & 0xc0);
        int attribute = screen[Rooms.BITMAP + (line >> 3) * 32 + column] & 0xff;
        int bright = attribute >> 3 & 8;
        int ink = colour(palette, attribute & 7 | bright), paper = colour(palette, attribute >> 3 & 7 | bright);
        for (int bit = 0; bit < 8; bit++) {
          picture.setRGB(column * 8 + bit, line, (screen[offset] << bit & 0x80) != 0 ? ink : paper);
        }
      }
      return picture;
    }

    private int colour(int[] palette, int index) {
      if (palette != null) return palette[index];
      int level = (index & 8) != 0 ? 0xff : 0xcd;
      return ((index & 2) != 0 ? level << 16 : 0) | ((index & 4) != 0 ? level << 8 : 0) | ((index & 1) != 0 ? level : 0);
    }
  }
}
