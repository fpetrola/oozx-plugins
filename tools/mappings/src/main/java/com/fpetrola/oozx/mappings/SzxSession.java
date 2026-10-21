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
import com.fpetrola.oozx.speccy.devices.MachineTool;
import com.fpetrola.oozx.speccy.devices.Session;
import com.fpetrola.oozx.speccy.pokes.PokFile;
import com.fpetrola.oozx.speccy.pokes.PokInstruction;
import com.fpetrola.oozx.speccy.pokes.PokesTool;
import com.google.inject.Inject;

import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A machine's window as an SZX: the machine in the format's blocks, and what is around it in one of
 * its own, OOZX, which another emulator passes over. What is around it is text, a key a line: where
 * the window is, whether it is paused, the file the machine came from, its processor and its turbo,
 * every setting of the machine and of its devices by the name it is declared under, and the pokes
 * applied to it.
 * <p>
 * Not the model nor the ROMs: the format chooses the machine, and choosing it again once it is read
 * starts it from cold.
 */
public final class SzxSession implements Session {

  static final String BLOCK = "OOZX";

  private final SzxFormat szx = new SzxFormat();
  /** The tools, asked what they left done on a window: the very ones its toolbar has. */
  @Inject
  private Set<MachineTool> tools = Set.of();

  public SzxSession() {
  }

  /** With the tools it asks, where nothing injects them. */
  public SzxSession(Set<MachineTool> tools) {
    this.tools = tools;
  }

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
    PokesTool pokes = pokes();
    if (pokes != null) keep(pokes.applied(window), around);
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
    PokesTool pokes = pokes();
    if (pokes != null) pokes.applied(window, appliedIn(around));
  }

  private PokesTool pokes() {
    return tools.stream().filter(PokesTool.class::isInstance).map(PokesTool.class::cast).findFirst().orElse(null);
  }

  /** Each poke applied, by its line of the .pok and what it overwrote, so that it can still be undone. */
  private static void keep(List<PokFile.PokeMod> applied, Map<String, String> around) {
    for (int at = 0; at < applied.size(); at++) {
      PokFile.PokeMod mod = applied.get(at);
      PokInstruction line = mod.getParsedInstruction();
      String key = "poke." + at + ".";
      around.put(key + "name", mod.getName());
      around.put(key + "line", mod.getRawInstruction());
      if (mod.getPokFileName() != null) around.put(key + "file", mod.getPokFileName());
      if (mod.getGameName() != null) around.put(key + "game", mod.getGameName());
      if (line.isApplied()) around.put(key + "before", line.getPreviousValue() + "," + line.getPreviousBank() + "," + line.getPreviousAddress());
    }
  }

  /** The same back: the memory already has them, from the file, so they are only written down. */
  private static List<PokFile.PokeMod> appliedIn(Map<String, String> around) {
    List<PokFile.PokeMod> applied = new ArrayList<>();
    for (int at = 0; around.containsKey("poke." + at + ".line"); at++) {
      String key = "poke." + at + ".";
      PokFile.PokeMod mod = new PokFile.PokeMod(around.get(key + "name"), around.get(key + "line"),
          around.get(key + "file"), around.get(key + "game"));
      if (around.containsKey(key + "before")) {
        String[] before = around.get(key + "before").split(",");
        mod.getParsedInstruction().setPreviousValue(number(before[0]));
        mod.getParsedInstruction().setPreviousBank(number(before[1]));
        mod.getParsedInstruction().setPreviousAddress(number(before[2]));
      }
      applied.add(mod);
    }
    return applied;
  }

  private static Integer number(String text) {
    return text.equals("null") ? null : Integer.valueOf(text);
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
