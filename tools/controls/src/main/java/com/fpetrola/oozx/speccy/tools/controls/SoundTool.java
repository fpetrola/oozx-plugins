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

package com.fpetrola.oozx.speccy.tools.controls;

import dev.crystal.plugins.api.Offers;

import com.fpetrola.oozx.EmulatorControl;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSlider;
import javax.swing.ToolTipManager;

/** El sonido: silencio con el boton, volumen con la regla del clic derecho. */
@Offers("Change the volume")
public class SoundTool implements MachineTool {

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("1F507.svg");
  }

  public String tooltip() {
    return "Mute/Unmute Sound - right-click for the volume";
  }

  /** Desde el menu del escritorio, sobre la maquina activa. */
  public javax.swing.KeyStroke key() {
    return javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M,
        java.awt.event.InputEvent.CTRL_DOWN_MASK);
  }

  public int place() {
    return 40;
  }

  public void use(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    machine.setGeneralOption("mute", !machine.isMuted());
    if (window instanceof java.awt.Component shown) shown.repaint();
  }

  /**
   * Dice lo que la maquina esta haciendo cada vez que se dibuja, y la regla el volumen cada vez que
   * aparece: nadie tiene que avisarles, ni al volver la ventana ni cuando otro cambia el sonido.
   */
  public AbstractButton button(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    Icon muted = Widgets.loadIcon("1F509.svg");
    Icon sounding = Widgets.loadIcon("1F507.svg");
    JButton button = new JButton() {
      public Icon getIcon() {
        return machine.isMuted() ? muted : sounding;
      }

      public String getToolTipText() {
        return machine.isMuted() ? "Unmute Sound" : "Mute Sound";
      }
    };
    ToolTipManager.sharedInstance().registerComponent(button);
    button.addActionListener(pressed -> use(window));
    JSlider volume = new JSlider(0, 100, machine.getVolume());
    Widgets.upright(volume, 24, 160);
    volume.addChangeListener(moved -> machine.setAudioOption("volume", volume.getValue()));
    volume.addHierarchyListener(changed -> {
      if (volume.isShowing()) volume.setValue(machine.getVolume());
    });
    Widgets.popUpOnRightClick(button, volume);
    return button;
  }

  public JMenuItem menuItem(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    JMenu menu = new JMenu("Volume (" + machine.getVolume() + "%)");
    JMenuItem louder = new JMenuItem("Louder");
    louder.addActionListener(e -> machine.setAudioOption("volume", Math.min(100, machine.getVolume() + 10)));
    JMenuItem quieter = new JMenuItem("Quieter");
    quieter.addActionListener(e -> machine.setAudioOption("volume", Math.max(0, machine.getVolume() - 10)));
    JCheckBoxMenuItem mute = new JCheckBoxMenuItem("Mute", machine.isMuted());
    mute.addActionListener(e -> use(window));
    menu.add(louder);
    menu.add(quieter);
    menu.addSeparator();
    menu.add(mute);
    return menu;
  }
}
