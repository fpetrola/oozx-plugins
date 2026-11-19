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


package com.fpetrola.oozx.speccy.tools.games;

import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.peripherals.EmulatorCore;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A game's music, played: the songs of its AY file to pick from, what the file says of itself, and the controls
 * of a player. What plays is a 128K nobody sees, started on the song as a snapshot; this window is all there is
 * of it on the desk, and closing it switches that machine off.
 */
class AyPlayer extends JInternalFrame {
  private final AyMusic music;
  private final Path file;
  private final List<String> songs;
  private final JList<String> list;
  private final JLabel now = new JLabel(" "), clock = new JLabel("0:00");
  private final JProgressBar gone = new JProgressBar();
  private final JButton pause = new JButton("\u23F8 Pause");
  private JInternalFrame machine;
  private EmulatorCore core;
  private int playing = -1, heard;
  private boolean paused;

  AyPlayer(String game, Path file) throws IOException {
    super(game == null ? file.getFileName().toString() : game + " - music", true, true, false, true);
    this.file = file;
    music = new AyMusic(file);
    songs = music.songs();
    JPanel heading = new JPanel();
    heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
    heading.setBackground(new Color(0x17171C));
    heading.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
    heading.add(line(game == null ? file.getFileName().toString() : game, 20f, Font.BOLD, Color.WHITE));
    heading.add(line(now, 14f, Font.PLAIN, new Color(0xD8D800)));
    heading.add(line(said("by", music.author()), 12f, Font.PLAIN, new Color(0xB9B9C6)));
    heading.add(line(said("", music.notes()), 12f, Font.PLAIN, new Color(0xB9B9C6)));
    heading.add(line(file.getFileName() + "  \u00b7  " + songs.size() + (songs.size() == 1 ? " song" : " songs"), 11f, Font.PLAIN, new Color(0x00C8C8)));
    list = new JList<>(songs.toArray(String[]::new));
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> in, Object song, int index, boolean chosen, boolean focused) {
        String length = music.seconds(index) > 0 ? "   " + time(music.seconds(index)) : "";
        return super.getListCellRendererComponent(in, (index == playing ? "\u25B6  " : "     ") + (index + 1) + ".  " + song + length, index, chosen, focused);
      }
    });
    list.setFixedCellHeight(26);
    list.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent pressed) {
        if (pressed.getClickCount() == 2) play(list.getSelectedIndex());
      }
    });
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
    controls.add(doing("\u23EE", "The song before", () -> play(Math.floorMod(playing - 1, songs.size()))));
    controls.add(doing("\u25B6 Play", "Play the song picked, from its start", () -> play(Math.max(0, list.getSelectedIndex()))));
    pause.setToolTipText("Hold the music where it is, or go on");
    pause.addActionListener(pressed -> paused(!paused));
    controls.add(pause);
    controls.add(doing("\u23F9 Stop", "Stop, and switch off the machine that plays", this::stop));
    controls.add(doing("\u23ED", "The song after", () -> play((playing + 1) % songs.size())));
    JPanel foot = new JPanel(new BorderLayout(8, 0));
    foot.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
    foot.add(gone, BorderLayout.CENTER);
    foot.add(clock, BorderLayout.EAST);
    JPanel below = new JPanel(new BorderLayout());
    below.add(controls, BorderLayout.NORTH);
    below.add(foot, BorderLayout.SOUTH);
    add(heading, BorderLayout.NORTH);
    add(new JScrollPane(list), BorderLayout.CENTER);
    add(below, BorderLayout.SOUTH);
    Timer passing = new Timer(500, tick -> passed());
    passing.start();
    addInternalFrameListener(new InternalFrameAdapter() {
      @Override
      public void internalFrameClosed(InternalFrameEvent closed) {
        passing.stop();
        stop();
      }
    });
    setSize(420, Math.min(520, 300 + 26 * songs.size()));
    play(0);
  }

  /** That song from its start, on a machine of its own that is not shown: the last one's is switched off first. */
  private void play(int song) {
    stop();
    try {
      // Beside what was brought and not inside it: a snapshot left among a zip's files would be taken for one of them.
      Path snapshot = file.getParent().resolveSibling(file.getFileName() + "-" + (song + 1) + ".sna");
      Files.write(snapshot, music.snapshot(song));
      EmulatorWindow window = Desk.theOne().machineFrom(snapshot.toFile());
      machine = (JInternalFrame) window;
      machine.setVisible(false);
      core = Desk.theOne().coreOf(machine);
      // Music is heard at the speed it was written for, whatever speed the desk starts its machines at.
      core.setGeneralOption("speed", 100);
      playing = song;
      heard = 0;
      now.setText(songs.get(song));
      gone.setMaximum(Math.max(1, music.seconds(song) * 2));
      gone.setIndeterminate(music.seconds(song) == 0);
      list.setSelectedIndex(song);
      list.repaint();
      toFront();
    } catch (IOException | RuntimeException notPlayed) {
      now.setText("Could not play it: " + notPlayed.getMessage());
    }
  }

  private void stop() {
    if (machine != null) machine.dispose();
    machine = null;
    core = null;
    playing = -1;
    paused = false;
    pause.setText("\u23F8 Pause");
    gone.setIndeterminate(false);
    gone.setValue(0);
    list.repaint();
  }

  private void paused(boolean held) {
    if (core == null) return;
    paused = held;
    core.setGeneralOption("pause", held);
    pause.setText(held ? "\u25B6 Go on" : "\u23F8 Pause");
  }

  /** Half a second more of the song, when it is playing; a song that says how long it is gives way to the next at its end. */
  private void passed() {
    if (playing < 0 || paused) return;
    heard++;
    clock.setText(time(heard / 2) + (music.seconds(playing) > 0 ? " / " + time(music.seconds(playing)) : ""));
    gone.setValue(heard);
    if (music.seconds(playing) > 0 && heard >= music.seconds(playing) * 2) {
      if (songs.size() > 1) play((playing + 1) % songs.size());
      else stop();
    }
  }

  private static String time(int seconds) {
    return seconds / 60 + ":" + String.format("%02d", seconds % 60);
  }

  private static String said(String before, String what) {
    return what == null || what.isBlank() ? " " : (before + " " + what).trim();
  }

  private static JButton doing(String text, String tip, Runnable what) {
    JButton button = new JButton(text);
    button.setToolTipText(tip);
    button.addActionListener(pressed -> what.run());
    return button;
  }

  private static JLabel line(String text, float size, int style, Color colour) {
    return line(new JLabel(text), size, style, colour);
  }

  private static JLabel line(JLabel line, float size, int style, Color colour) {
    line.setFont(line.getFont().deriveFont(style, size));
    line.setForeground(colour);
    line.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
    return line;
  }
}
