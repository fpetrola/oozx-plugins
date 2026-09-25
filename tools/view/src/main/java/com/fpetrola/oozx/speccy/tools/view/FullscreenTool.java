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

package com.fpetrola.oozx.speccy.tools.view;

import dev.crystal.plugins.api.Offers;

import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * La pantalla sola, llenando el monitor. El panel se muda a una ventana sin bordes y vuelve, en
 * vez de dibujarse en otro lado, asi la maquina sigue andando y el teclado sigue funcionando.
 */
@Offers("Play in full screen")
public class FullscreenTool implements MachineTool {

  /** Donde estaba cada pantalla y la ventana que la tiene ahora, mientras esta a pantalla completa. */
  private final Map<EmulatorWindow, Away> away = new WeakHashMap<>();

  private record Away(JDialog whole, Container home, Object where) {
  }

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("1F4FA.svg");
  }

  public String tooltip() {
    return "Fullscreen (Escape to leave)";
  }

  public int place() {
    return 70;
  }

  public void use(EmulatorWindow window) {
    JComponent panel = window.picture();
    Away gone = away.remove(window);
    if (gone != null) {
      gone.whole().dispose();
      gone.home().add(panel, gone.where());
      gone.home().revalidate();
      gone.home().repaint();
    } else {
      Container home = panel.getParent();
      Object where = home.getLayout() instanceof java.awt.BorderLayout border
          ? border.getConstraints(panel) : null;
      Window owner = SwingUtilities.getWindowAncestor(panel);
      JDialog whole = new JDialog(owner);
      whole.setUndecorated(true);
      whole.getContentPane().setBackground(Color.BLACK);
      whole.getRootPane().registerKeyboardAction(e -> use(window),
          KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
      whole.add(panel);
      whole.setBounds(owner.getGraphicsConfiguration().getBounds());
      away.put(window, new Away(whole, home, where));
      whole.setVisible(true);
    }
    panel.requestFocusInWindow();
  }

  public JMenuItem menuItem(EmulatorWindow window) {
    JCheckBoxMenuItem whole = new JCheckBoxMenuItem("Full screen", away.containsKey(window));
    whole.addActionListener(e -> use(window));
    return whole;
  }

  @Override
  public void closed(EmulatorWindow window) {
    away.remove(window);
  }
}
