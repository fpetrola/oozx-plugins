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

import com.fpetrola.oozx.EmulatorControl;
import com.fpetrola.oozx.speccy.config.OOZxConfiguration;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSlider;
import java.util.Map;
import java.util.WeakHashMap;

/** El sonido: silencio con el boton, volumen con la regla del clic derecho. */
public class SoundTool implements MachineTool {

  /** El boton de cada ventana, para que al reabrirla muestre si quedo en silencio. */
  private final Map<EmulatorWindow, JButton> buttons = new WeakHashMap<>();

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("1F507.svg");
  }

  public String tooltip() {
    return "Mute/Unmute Sound - right-click for the volume";
  }

  public int place() {
    return 40;
  }

  public void use(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    machine.setGeneralOption("mute", !machine.isMuted());
    shows(window);
  }

  public AbstractButton button(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    JButton button = new JButton();
    buttons.put(window, button);
    button.addActionListener(pressed -> use(window));
    JSlider volume = new JSlider(0, 100, machine.getVolume());
    Widgets.upright(volume, 24, 160);
    volume.addChangeListener(moved -> machine.setAudioOption("volume", volume.getValue()));
    Widgets.popUpOnRightClick(button, volume);
    shows(window);
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

  /** La ventana vuelve en silencio si asi quedo: la maquina ya lo sabe, el boton se entera. */
  public void restore(EmulatorWindow window, OOZxConfiguration.WindowState from) {
    shows(window);
  }

  private void shows(EmulatorWindow window) {
    JButton button = buttons.get(window);
    if (button == null) return;
    boolean muted = TheMachine.of(window).isMuted();
    button.setIcon(Widgets.loadIcon(muted ? "1F509.svg" : "1F507.svg"));
    button.setToolTipText(muted ? "Unmute Sound" : "Mute Sound");
  }
}
