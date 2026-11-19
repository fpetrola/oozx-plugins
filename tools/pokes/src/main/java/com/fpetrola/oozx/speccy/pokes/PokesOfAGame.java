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

package com.fpetrola.oozx.speccy.pokes;

import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Arrays;
import java.util.List;

/** The pokes of a game, where what is known of the game is shown: each one with what it does, and the way to put them in. */
public class PokesOfAGame {
  private final PokesManager pokes = PokesManager.shared();

  public String what() {
    return "Pokes";
  }

  public int howMany(Desk.Game game) {
    return byName(found(game)).size();
  }

  public JComponent page(Desk.Game game) {
    List<PokFile> found = found(game);
    String[][] rows = byName(found).entrySet().stream()
        .map(poke -> new String[]{poke.getKey(), String.join("   ", poke.getValue())}).toArray(String[][]::new);
    JTable table = new JTable(rows, new String[]{"Poke", "What it writes"});
    table.setDefaultEditor(Object.class, null);
    table.setRowHeight(26);
    table.setShowVerticalLines(false);
    JButton putIn = new JButton("Choose and apply to the running game...");
    JPanel page = new JPanel(new BorderLayout(0, 8));
    page.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));
    putIn.addActionListener(pressed -> {
      EmulatorWindow playing = playing(game, page);
      if (playing == null) {
        JOptionPane.showMessageDialog(page, "No machine on the desk is running this game.", "Pokes", JOptionPane.INFORMATION_MESSAGE);
      } else {
        PokesTool.choose(playing, game.title(), found);
      }
    });
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    top.add(putIn);
    top.add(new JLabel("   " + rows.length + " pokes, from " + found.size() + " listing" + (found.size() == 1 ? "" : "s")));
    page.add(top, BorderLayout.NORTH);
    JPanel framed = new JPanel(new BorderLayout());
    framed.add(table.getTableHeader(), BorderLayout.NORTH);
    framed.add(table, BorderLayout.CENTER);
    page.add(framed, BorderLayout.CENTER);
    return page;
  }

  /** Each poke once, with every line it writes: a poke is listed a line at a time. */
  private static java.util.Map<String, List<String>> byName(List<PokFile> found) {
    return found.stream().flatMap(file -> file.getMods().stream()).collect(java.util.stream.Collectors.groupingBy(
        PokFile.PokeMod::getName, java.util.LinkedHashMap::new,
        java.util.stream.Collectors.mapping(PokFile.PokeMod::getRawInstruction, java.util.stream.Collectors.toList())));
  }

  private List<PokFile> found(Desk.Game game) {
    return pokes.findPokesForEntry(game.id());
  }

  /** The machine on this desk that was started from that game's file. */
  private static EmulatorWindow playing(Desk.Game game, JComponent from) {
    JDesktopPane desk = (JDesktopPane) SwingUtilities.getAncestorOfClass(JDesktopPane.class, from);
    return desk == null || game.file() == null ? null : Arrays.stream(desk.getAllFrames())
        .filter(EmulatorWindow.class::isInstance).map(EmulatorWindow.class::cast)
        .filter(window -> window.machine() != null && window.machine().control != null
            && game.file().equals(window.machine().control.getFilename())).findFirst().orElse(null);
  }
}
