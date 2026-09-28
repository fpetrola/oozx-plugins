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

package model.tests.formats;

import com.fpetrola.emulation.helpers.snapshots.AY8912State;
import com.fpetrola.emulation.helpers.snapshots.MemoryState;
import com.fpetrola.emulation.helpers.snapshots.SpectrumState;
import com.fpetrola.emulation.helpers.snapshots.Z80State;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A snapshot said field by field, one line each: what a golden holds, and what two readers are
 * compared in.
 * <p>
 * The names are the ones the property catalog is going to have - a, bc, iff1, border, port7ffd,
 * page.5 - and not those of the state the readers fill today, so that the goldens written now
 * are still the goldens when the readers are replaced.
 */
final class Fields {

  /** The fields libspectrum and the Java readers can both say, in the order they are listed. */
  private static final List<String> COMMON = List.of(
      "machine",
      "a", "f", "bc", "de", "hl", "a'", "f'", "bc'", "de'", "hl'", "ix", "iy", "sp", "pc", "i", "r", "memptr",
      "iff1", "iff2", "im", "halted", "eiPending",
      "tstates", "border", "issue2", "joystick",
      "port7ffd", "port1ffd",
      "ay", "ay.selected", "ay.registers",
      "page.0", "page.1", "page.2", "page.3", "page.4", "page.5", "page.6", "page.7");

  /** Which of those each format has a place for: the first draft of the format by property matrix. */
  private static final Map<String, Set<String>> CARRIED = Map.of(
      "sp", Set.of("machine", "a", "f", "bc", "de", "hl", "a'", "f'", "bc'", "de'", "hl'", "ix", "iy", "sp", "pc", "i", "r",
          "iff1", "iff2", "im", "border", "page.0", "page.1", "page.2", "page.3", "page.4", "page.5", "page.6", "page.7"),
      "sna", Set.of("machine", "a", "f", "bc", "de", "hl", "a'", "f'", "bc'", "de'", "hl'", "ix", "iy", "sp", "pc", "i", "r",
          "iff1", "iff2", "im", "border", "port7ffd",
          "page.0", "page.1", "page.2", "page.3", "page.4", "page.5", "page.6", "page.7"),
      "z80", Set.of("machine", "a", "f", "bc", "de", "hl", "a'", "f'", "bc'", "de'", "hl'", "ix", "iy", "sp", "pc", "i", "r",
          "iff1", "iff2", "im", "tstates", "border", "issue2", "joystick", "port7ffd", "port1ffd",
          "ay", "ay.selected", "ay.registers",
          "page.0", "page.1", "page.2", "page.3", "page.4", "page.5", "page.6", "page.7"),
      "szx", Set.copyOf(COMMON));

  private static final String ZERO_PAGE = digest(new byte[0x4000]);

  private final Map<String, String> values = new LinkedHashMap<>();

  private Fields() {
  }

  /**
   * Everything today's readers leave in the state, including what only they know about. A file can
   * leave parts of it empty - an SZX made of one block says nothing of the processor - and that is
   * said as absent, not taken for a crash of the reader.
   */
  static Fields of(SpectrumState state) {
    Fields fields = new Fields();
    fields.put("machine", state.getSpectrumModel() == null ? "none" : state.getSpectrumModel().name());
    Z80State z80 = state.getZ80State();
    if (z80 == null) {
      fields.put("processor", "absent");
    } else {
      processor(fields, z80);
    }
    return rest(fields, state);
  }

  private static void processor(Fields fields, Z80State z80) {
    fields.put("a", hex8(z80.getRegA())).put("f", hex8(z80.getRegF()))
        .put("bc", hex16(z80.getRegBC())).put("de", hex16(z80.getRegDE())).put("hl", hex16(z80.getRegHL()))
        .put("a'", hex8(z80.getRegAx())).put("f'", hex8(z80.getRegFx()))
        .put("bc'", hex16(z80.getRegBCx())).put("de'", hex16(z80.getRegDEx())).put("hl'", hex16(z80.getRegHLx()))
        .put("ix", hex16(z80.getRegIX())).put("iy", hex16(z80.getRegIY()))
        .put("sp", hex16(z80.getRegSP())).put("pc", hex16(z80.getRegPC()))
        .put("i", hex8(z80.getRegI())).put("r", hex8(z80.getRegR())).put("memptr", hex16(z80.getMemPtr()))
        .put("iff1", z80.isIFF1()).put("iff2", z80.isIFF2()).put("im", z80.getIM())
        .put("halted", z80.isHalted()).put("eiPending", z80.isPendingEI()).put("flagQ", z80.isFlagQ());
  }

  private static Fields rest(Fields fields, SpectrumState state) {
    fields.put("tstates", state.getTstates()).put("border", state.getBorder())
        .put("issue2", state.isIssue2()).put("joystick", state.getJoystick() == null ? "none" : state.getJoystick().name())
        .put("port7ffd", hex8(state.getPort7ffd())).put("port1ffd", hex8(state.getPort1ffd()));
    AY8912State ay = state.getAY8912State();
    fields.put("ay", state.isEnabledAY() && ay != null ? "fitted" : "absent")
        .put("ay.enabled", state.isEnabledAY()).put("ay.on48k", state.isEnabledAYon48k())
        .put("ay.selected", ay == null ? "absent" : hex8(ay.getAddressLatch()))
        .put("ay.registers", ay == null || ay.getRegAY() == null ? "absent" : registers(ay.getRegAY()));
    fields.put("ulaplus.enabled", state.isULAPlusEnabled()).put("ulaplus.active", state.isULAPlusActive())
        .put("ulaplus.group", hex8(state.getPaletteGroup()))
        .put("ulaplus.palette", state.getULAPlusPalette() == null ? "absent" : registers(state.getULAPlusPalette()));
    MemoryState memory = state.getMemoryState();
    fields.put("multiface", state.isMultiface());
    fields.put("if1", state.isConnectedIF1()).put("if1.microdrives", state.getNumMicrodrives());
    fields.put("lec", state.isConnectedLec());
    if (memory == null) {
      fields.put("memory", "absent");
      return fields;
    }
    fields.put("multiface.128on48k", memory.isMf128on48k()).put("multiface.paged", memory.isMultifacePaged())
        .put("multiface.locked", memory.isMultifaceLocked()).put("multiface.ram", pageOf(memory.getMultifaceRam()))
        .put("if1.paged", memory.isIF1RomPaged())
        .put("if2.paged", memory.isIF2RomPaged()).put("if2.rom", pageOf(memory.getIF2Rom()))
        .put("lec.port", hex8(memory.getPortFD()));
    for (int page = 0; page < 16; page++) {
      if (memory.getLecPageRam(page) != null) {
        fields.put("lec.page." + page, pageOf(memory.getLecPageRam(page)));
      }
    }
    for (int page = 0; page < 8; page++) {
      fields.put("page." + page, pageOf(memory.getPageRam(page)));
    }
    return fields;
  }

  /** What libspectrum read, in the same names. Ports and chips that the machine has no use for are said as absent. */
  static Fields ofReference(com.sun.jna.Pointer snap) {
    Libspectrum lib = Libspectrum.INSTANCE;
    int machine = lib.libspectrum_snap_machine(snap);
    int can = lib.libspectrum_machine_capabilities(machine);
    Fields fields = new Fields();
    fields.put("machine", machineName(machine))
        .put("a", hex8(lib.libspectrum_snap_a(snap))).put("f", hex8(lib.libspectrum_snap_f(snap)))
        .put("bc", hex16(lib.libspectrum_snap_bc(snap))).put("de", hex16(lib.libspectrum_snap_de(snap)))
        .put("hl", hex16(lib.libspectrum_snap_hl(snap)))
        .put("a'", hex8(lib.libspectrum_snap_a_(snap))).put("f'", hex8(lib.libspectrum_snap_f_(snap)))
        .put("bc'", hex16(lib.libspectrum_snap_bc_(snap))).put("de'", hex16(lib.libspectrum_snap_de_(snap)))
        .put("hl'", hex16(lib.libspectrum_snap_hl_(snap)))
        .put("ix", hex16(lib.libspectrum_snap_ix(snap))).put("iy", hex16(lib.libspectrum_snap_iy(snap)))
        .put("sp", hex16(lib.libspectrum_snap_sp(snap))).put("pc", hex16(lib.libspectrum_snap_pc(snap)))
        .put("i", hex8(lib.libspectrum_snap_i(snap))).put("r", hex8(lib.libspectrum_snap_r(snap)))
        .put("memptr", hex16(lib.libspectrum_snap_memptr(snap)))
        .put("iff1", lib.libspectrum_snap_iff1(snap) != 0).put("iff2", lib.libspectrum_snap_iff2(snap) != 0)
        .put("im", "IM" + lib.libspectrum_snap_im(snap))
        .put("halted", lib.libspectrum_snap_halted(snap) != 0)
        .put("eiPending", lib.libspectrum_snap_last_instruction_ei(snap) != 0)
        .put("tstates", lib.libspectrum_snap_tstates(snap))
        .put("border", lib.libspectrum_snap_out_ula(snap) & 0x07)
        .put("issue2", lib.libspectrum_snap_issue2(snap) != 0)
        .put("joystick", joystickName(lib.libspectrum_snap_joystick_active_count(snap).longValue() == 0
            ? Libspectrum.JOYSTICK_NONE : lib.libspectrum_snap_joystick_list(snap, 0)))
        .put("port7ffd", (can & Libspectrum.CAPABILITY_128_MEMORY) != 0
            ? hex8(lib.libspectrum_snap_out_128_memoryport(snap)) : "absent")
        .put("port1ffd", (can & (Libspectrum.CAPABILITY_PLUS3_MEMORY | Libspectrum.CAPABILITY_SCORP_MEMORY)) != 0
            ? hex8(lib.libspectrum_snap_out_plus3_memoryport(snap)) : "absent");
    boolean ay = (can & Libspectrum.CAPABILITY_AY) != 0
        || lib.libspectrum_snap_fuller_box_active(snap) != 0 || lib.libspectrum_snap_melodik_active(snap) != 0;
    int[] registers = new int[16];
    for (int register = 0; register < 16; register++) {
      registers[register] = lib.libspectrum_snap_ay_registers(snap, register) & 0xff;
    }
    fields.put("ay", ay ? "fitted" : "absent")
        .put("ay.selected", ay ? hex8(lib.libspectrum_snap_out_ay_registerport(snap)) : "absent")
        .put("ay.registers", ay ? registers(registers) : "absent");
    for (int page = 0; page < 8; page++) {
      com.sun.jna.Pointer ram = lib.libspectrum_snap_pages(snap, page);
      fields.put("page." + page, pageOf(ram == null ? null : ram.getByteArray(0, 0x4000)));
    }
    return fields;
  }

  static Fields refused(String reason) {
    return new Fields().put("refused", reason);
  }

  boolean isRefused() {
    return values.containsKey("refused");
  }

  /** The parts of the state the reader left empty: "processor", "memory". */
  List<String> absentParts() {
    return java.util.stream.Stream.of("processor", "memory").filter(part -> "absent".equals(values.get(part))).toList();
  }

  /** Why it was refused, or null: "FILE_SIZE_ERROR", or "crashed: ArrayIndexOutOfBoundsException". */
  String refusal() {
    return values.get("refused");
  }

  boolean crashed() {
    return isRefused() && refusal().startsWith("crashed: ");
  }

  /**
   * Only what that format has a place for, said as both readers would: a port or a chip the
   * machine does not have is absent, and a page that was never written reads as zeros.
   */
  Fields carriedBy(String format) {
    Fields carried = new Fields();
    boolean paged = !"absent".equals(values.get("port7ffd")) && !isFortyEightK(values.get("machine"));
    boolean plus3 = isPlus3Paged(values.get("machine")) && !"absent".equals(values.get("port1ffd"));
    boolean ay = "fitted".equals(values.get("ay"));
    for (String name : COMMON) {
      if (!CARRIED.get(format).contains(name) || !values.containsKey(name)) {
        continue;
      }
      String value = values.get(name);
      if (name.equals("port7ffd") && !paged || name.equals("port1ffd") && !plus3
          || name.startsWith("ay.") && !ay) {
        value = "absent";
      }
      if (name.startsWith("page.") && value.equals("absent")) {
        value = ZERO_PAGE;
      }
      carried.values.put(name, value);
    }
    return carried;
  }

  /** Every field whose value differs, said as "name: this, that". */
  List<String> differencesFrom(Fields other) {
    List<String> differences = new ArrayList<>();
    for (Map.Entry<String, String> field : values.entrySet()) {
      String theirs = other.values.getOrDefault(field.getKey(), "missing");
      if (!field.getValue().equals(theirs)) {
        differences.add(field.getKey() + ": " + field.getValue() + ", " + theirs);
      }
    }
    for (String name : other.values.keySet()) {
      if (!values.containsKey(name)) {
        differences.add(name + ": missing, " + other.values.get(name));
      }
    }
    return differences;
  }

  static Fields parse(String text) {
    Fields fields = new Fields();
    for (String line : text.split("\n")) {
      int equals = line.indexOf(" = ");
      if (equals > 0) {
        fields.values.put(line.substring(0, equals), line.substring(equals + 3));
      }
    }
    return fields;
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder();
    values.forEach((name, value) -> text.append(name).append(" = ").append(value).append('\n'));
    return text.toString();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Fields fields && values.equals(fields.values);
  }

  @Override
  public int hashCode() {
    return values.hashCode();
  }

  private Fields put(String name, Object value) {
    values.put(name, String.valueOf(value));
    return this;
  }

  static String digest(byte[] bytes) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
      return "sha256:" + HexFormat.of().formatHex(hash, 0, 8) + " (" + bytes.length + ")";
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static String pageOf(byte[] page) {
    return page == null ? "absent" : digest(page);
  }

  private static String registers(int[] values) {
    StringBuilder text = new StringBuilder();
    for (int value : values) {
      text.append(text.isEmpty() ? "" : " ").append(String.format("%02x", value & 0xff));
    }
    return text.toString();
  }

  private static String hex8(int value) {
    return String.format("0x%02x", value & 0xff);
  }

  private static String hex16(int value) {
    return String.format("0x%04x", value & 0xffff);
  }

  private static boolean isFortyEightK(String machine) {
    return machine != null && (machine.equals("SPECTRUM16K") || machine.equals("SPECTRUM48K")
        || machine.equals("48_NTSC") || machine.equals("TC2048"));
  }

  private static boolean isPlus3Paged(String machine) {
    return machine != null && (machine.equals("SPECTRUMPLUS2A") || machine.equals("SPECTRUMPLUS3")
        || machine.equals("PLUS3E") || machine.equals("SCORP"));
  }

  /** libspectrum's machines in the names of today's MachineTypes where there is one, and in libspectrum's where not. */
  private static String machineName(int machine) {
    return switch (machine) {
      case Libspectrum.MACHINE_16 -> "SPECTRUM16K";
      case Libspectrum.MACHINE_48 -> "SPECTRUM48K";
      case Libspectrum.MACHINE_128 -> "SPECTRUM128K";
      case Libspectrum.MACHINE_PLUS2 -> "SPECTRUMPLUS2";
      case Libspectrum.MACHINE_PLUS2A -> "SPECTRUMPLUS2A";
      case Libspectrum.MACHINE_PLUS3 -> "SPECTRUMPLUS3";
      case Libspectrum.MACHINE_PENT -> "PENTAGON";
      case Libspectrum.MACHINE_SCORP -> "SCORP";
      case Libspectrum.MACHINE_TC2048 -> "TC2048";
      case Libspectrum.MACHINE_TC2068 -> "TC2068";
      case Libspectrum.MACHINE_TS2068 -> "TS2068";
      case Libspectrum.MACHINE_PLUS3E -> "PLUS3E";
      case Libspectrum.MACHINE_SE -> "SE";
      case Libspectrum.MACHINE_PENT512 -> "PENT512";
      case Libspectrum.MACHINE_PENT1024 -> "PENT1024";
      case Libspectrum.MACHINE_48_NTSC -> "48_NTSC";
      case Libspectrum.MACHINE_128E -> "128E";
      default -> "UNKNOWN";
    };
  }

  /** libspectrum's joysticks in the names of today's JoystickModel. */
  private static String joystickName(int joystick) {
    return switch (joystick) {
      case Libspectrum.JOYSTICK_CURSOR -> "CURSOR";
      case Libspectrum.JOYSTICK_KEMPSTON -> "KEMPSTON";
      case Libspectrum.JOYSTICK_SINCLAIR_1 -> "SINCLAIR1";
      case Libspectrum.JOYSTICK_SINCLAIR_2 -> "SINCLAIR2";
      case Libspectrum.JOYSTICK_FULLER -> "FULLER";
      case Libspectrum.JOYSTICK_TIMEX_1 -> "TIMEX1";
      case Libspectrum.JOYSTICK_TIMEX_2 -> "TIMEX2";
      default -> "NONE";
    };
  }
}
