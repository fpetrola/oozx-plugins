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
import com.fpetrola.oozx.EmulatorListener;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La velocidad: el cohete para ir a fondo, la regla del clic derecho para elegir, y en la barra
 * de estado a cuanto va de verdad.
 */
@Offers("Run the machine faster")
public class TurboTool implements MachineTool {

  /** Lo mas que pide la regla, y lo que pide el cohete al apretarlo. */
  static final int TOP_SPEED = 40000;
  /** Donde se juntan las dos mitades de la regla: la izquierda es a lo que se juega, la derecha el resto. */
  static final int KNEE_SPEED = 1000;
  private static final int HALF = 500;

  public javax.swing.Icon icon() {
    return Widgets.loadIcon("1F680.svg");
  }

  public String tooltip() {
    return "Full speed - right-click for a speed";
  }

  /** Desde el menu del escritorio, sobre la maquina activa. */
  public javax.swing.KeyStroke key() {
    return javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T,
        java.awt.event.InputEvent.CTRL_DOWN_MASK);
  }

  public int place() {
    return 10;
  }

  public void use(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    machine.setGeneralOption("speed", machine.getEmulationSpeed() >= TOP_SPEED ? 100 : TOP_SPEED);
  }

  public AbstractButton button(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    ImageIcon rocket = Widgets.loadIcon("1F680.svg");
    JButton button = new JButton(rocket);
    button.setToolTipText(tooltip());
    JSlider slider = new JSlider(0, 2 * HALF, positionOf(100));
    Map<Integer, JComponent> labels = new java.util.Hashtable<>();
    for (int speed : new int[]{25, 500, KNEE_SPEED, 20000, TOP_SPEED}) {
      labels.put(positionOf(speed), new JLabel(speed + "%"));
    }
    slider.setLabelTable((java.util.Hashtable<Integer, JComponent>) labels);
    slider.setPaintLabels(true);
    Widgets.upright(slider, 64, 220);
    boolean[] reflecting = {false};
    // La regla y el cohete dicen la velocidad: el cohete se apaga a medida que llega arriba.
    java.util.function.IntConsumer reflect = speed -> {
      reflecting[0] = true;
      slider.setValue(positionOf(speed));
      reflecting[0] = false;
      button.setIcon(Widgets.greyed(rocket, (speed - 100) / (float) (TOP_SPEED - 100)));
    };
    slider.addChangeListener(moved -> {
      if (!reflecting[0]) {
        int speed = speedAt(slider.getValue());
        machine.setGeneralOption("speed", speed);
        reflect.accept(speed);
      }
    });
    button.addActionListener(pressed -> {
      use(window);
      reflect.accept((int) machine.getEmulationSpeed());
    });
    Widgets.popUpOnRightClick(button, slider);
    reflect.accept((int) machine.getEmulationSpeed());
    return button;
  }

  /** Las velocidades con nombre, porque sin barra a la vista una regla no sirve. */
  public JMenuItem menuItem(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    // La maquina y no la regla: las posiciones de la regla son enteros sobre cuarenta mil, asi
    // que el tiempo real vuelve de ella como noventa y nueve y nada quedaria marcado.
    int running = (int) Math.round(machine.getEmulationSpeed());
    Map<String, Integer> speeds = new LinkedHashMap<>();
    speeds.put("Half", 50);
    speeds.put("Normal", 100);
    speeds.put("Double", 200);
    speeds.put("Full", TOP_SPEED);
    JMenu menu = new JMenu("Speed");
    javax.swing.ButtonGroup one = new javax.swing.ButtonGroup();
    speeds.forEach((name, speed) -> {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(name, running == speed);
      item.addActionListener(chosen -> machine.setGeneralOption("speed", speed));
      one.add(item);
      menu.add(item);
    });
    return menu;
  }

  /**
   * A cuanto va, contra la maquina real: lleno es la velocidad de una Spectrum. El turbo pasa el
   * final y la deja llena, que es para lo que esta el cohete al lado.
   */
  public JComponent status(EmulatorWindow window) {
    EmulatorControl machine = TheMachine.of(window);
    JProgressBar bar = new JProgressBar(0, 100);
    bar.setStringPainted(true);
    bar.setPreferredSize(new Dimension(64, 20));
    JLabel turbo = new JLabel(Widgets.loadIcon("1F680.svg"));
    java.util.function.DoubleConsumer speedIs = speed -> {
      bar.setValue((int) Math.min(100, Math.round(speed)));
      bar.setString(String.format("%.0f%%", speed));
    };
    java.util.function.Consumer<Boolean> turboIs = on -> {
      turbo.setEnabled(on);
      turbo.setToolTipText(on ? "Turbo: running at full speed" : "Turbo off");
    };
    speedIs.accept(machine.getEmulationSpeed());
    turboIs.accept(machine.isTurboMode());
    machine.addEmulatorListener(new EmulatorListener() {
      public void onEmulationSpeedChanged(double speed) {
        SwingUtilities.invokeLater(() -> speedIs.accept(speed));
      }

      public void onTurboModeChanged(boolean on) {
        SwingUtilities.invokeLater(() -> turboIs.accept(on));
      }
    });
    JPanel both = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    both.setOpaque(false);
    both.add(bar);
    both.add(turbo);
    return both;
  }

  /** La velocidad en una posicion: la mitad izquierda va de un cuarto a la rodilla, la derecha de la rodilla al tope. */
  static int speedAt(int position) {
    if (position <= HALF) return 25 + Math.round((KNEE_SPEED - 25) * position / (float) HALF);
    return KNEE_SPEED + Math.round((TOP_SPEED - KNEE_SPEED) * (position - HALF) / (float) HALF);
  }

  static int positionOf(double speed) {
    if (speed <= KNEE_SPEED) return Math.max(0, Math.round((float) (speed - 25) * HALF / (KNEE_SPEED - 25)));
    return Math.min(2 * HALF, HALF + Math.round((float) (speed - KNEE_SPEED) * HALF / (TOP_SPEED - KNEE_SPEED)));
  }
}
