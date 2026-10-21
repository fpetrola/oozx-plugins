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

package com.fpetrola.oozx.mappings;

import com.fpetrola.emulation.helpers.snapshots.SnapshotException;
import com.fpetrola.oozx.EmulatorControl;
import com.fpetrola.oozx.config.Settings;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.EmulatorWindow;
import com.fpetrola.oozx.speccy.devices.Session;

import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A machine's window as an SZX: the machine in the format's blocks, and what is around it in one of
 * its own, OOZX, which another emulator passes over. What is around it is text, a key a line: where
 * the window is, whether it is paused, the file the machine came from, its processor and its turbo,
 * and every setting of the machine and of its devices by the name it is declared under.
 * <p>
 * Not the model nor the ROMs: the format chooses the machine, and choosing it again once it is read
 * starts it from cold.
 */
public final class SzxSession implements Session {

  static final String BLOCK = "OOZX";

  private final SzxFormat szx = new SzxFormat();

  @Override
  public String extension() {
    return "szx";
  }

  @Override
  public byte[] write(EmulatorWindow window) throws SnapshotException {
    Map<String, String> around = new TreeMap<>();
    Component place = placeOf(window);
    around.put("window.x", String.valueOf(place.getX()));
    around.put("window.y", String.valueOf(place.getY()));
    around.put("window.width", String.valueOf(place.getWidth()));
    around.put("window.height", String.valueOf(place.getHeight()));
    EmulatorControl control = window.machine().control;
    if (control != null) {
      around.put("paused", String.valueOf(control.isPaused()));
      around.put("turbo", String.valueOf(control.isTurboMode()));
      if (control.getProcessor() != null) around.put("processor", control.getProcessor());
      if (control.getFilename() != null && !control.getFilename().isEmpty()) around.put("file", control.getFilename());
    }
    for (Settings.Configurable settings : window.machine().settings().devices()) {
      for (String property : settings.device().properties()) {
        Object value = settings.values().get(property);
        if (value != null) around.put(settings.device().name() + "." + property, value instanceof Enum<?> named ? named.name() : String.valueOf(value));
      }
    }
    String text = around.entrySet().stream().map(line -> line.getKey() + "=" + line.getValue()).collect(Collectors.joining("\n"));
    return szx.write(window.machine(), Map.of(BLOCK, text.getBytes(StandardCharsets.UTF_8)), note -> { });
  }

  @Override
  public void bringBack(byte[] file, EmulatorWindow window) {
    byte[] block = szx.carried(file).get(BLOCK);
    if (block == null) return;
    Map<String, String> around = new TreeMap<>();
    for (String line : new String(block, StandardCharsets.UTF_8).split("\n")) {
      int equals = line.indexOf('=');
      if (equals > 0) around.put(line.substring(0, equals), line.substring(equals + 1));
    }
    for (Settings.Configurable settings : window.machine().settings().devices()) {
      for (String property : settings.device().properties()) {
        String value = around.get(settings.device().name() + "." + property);
        if (value == null) continue;
        try {
          settings.values().set(property, typed(value, settings.device().typeOf(property)));
        } catch (RuntimeException unreadable) {
          // A value a version that meant something else by it wrote: this one stays as the machine starts.
        }
      }
    }
    EmulatorControl control = window.machine().control;
    if (control != null) {
      if (around.containsKey("processor")) control.setProcessor(around.get("processor"));
      if (around.containsKey("turbo")) control.setGeneralOption("turbo", Boolean.parseBoolean(around.get("turbo")));
      if (around.containsKey("paused")) control.setGeneralOption("pause", Boolean.parseBoolean(around.get("paused")));
      control.setFilename(around.get("file"));
    }
    if (around.containsKey("window.width")) {
      placeOf(window).setBounds(Integer.parseInt(around.get("window.x")), Integer.parseInt(around.get("window.y")),
          Integer.parseInt(around.get("window.width")), Integer.parseInt(around.get("window.height")));
    }
  }

  /** The window itself where it is one, and otherwise what it shows the machine on. */
  private static Component placeOf(EmulatorWindow window) {
    return window instanceof Component itself ? itself : window.picture();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object typed(String value, Class<?> type) {
    if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
    if (type == int.class || type == Integer.class) return Integer.parseInt(value);
    if (type == long.class || type == Long.class) return Long.parseLong(value);
    if (type == double.class || type == Double.class) return Double.parseDouble(value);
    if (type == float.class || type == Float.class) return Float.parseFloat(value);
    if (type.isEnum()) return Enum.valueOf((Class) type, value);
    return value;
  }
}
