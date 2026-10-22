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

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * libspectrum, as far as the oracle needs it: read a snapshot, write it back out in another
 * format, and say what it read, field by field. Bound here rather than borrowed from the bridge,
 * which only has the five accessors it needed.
 */
public interface Libspectrum extends Library {

  /** libspectrum_id_t, counted from its header: DSK is 6, the CPC ones came later and went to the end. */
  int SNA = 2, Z80 = 3, SP = 12, SZX = 15;

  /** libspectrum_machine. */
  int MACHINE_48 = 0, MACHINE_TC2048 = 1, MACHINE_128 = 2, MACHINE_PLUS2 = 3, MACHINE_PENT = 4,
      MACHINE_PLUS2A = 5, MACHINE_PLUS3 = 6, MACHINE_UNKNOWN = 7, MACHINE_16 = 8, MACHINE_TC2068 = 9,
      MACHINE_SCORP = 10, MACHINE_PLUS3E = 11, MACHINE_SE = 12, MACHINE_TS2068 = 13, MACHINE_PENT512 = 14,
      MACHINE_PENT1024 = 15, MACHINE_48_NTSC = 16, MACHINE_128E = 17;

  /** The capabilities that decide which fields of a snapshot mean anything on a machine. */
  int CAPABILITY_AY = 1, CAPABILITY_128_MEMORY = 1 << 1, CAPABILITY_PLUS3_MEMORY = 1 << 2,
      CAPABILITY_SCORP_MEMORY = 1 << 10;

  /** libspectrum_joystick. */
  int JOYSTICK_NONE = 0, JOYSTICK_CURSOR = 1, JOYSTICK_KEMPSTON = 2, JOYSTICK_SINCLAIR_1 = 3,
      JOYSTICK_SINCLAIR_2 = 4, JOYSTICK_TIMEX_1 = 5, JOYSTICK_TIMEX_2 = 6, JOYSTICK_FULLER = 7;

  Libspectrum INSTANCE = Native.load("spectrum", Libspectrum.class);

  int libspectrum_init();

  int libspectrum_machine_capabilities(int machine);

  Pointer libspectrum_snap_alloc();

  int libspectrum_snap_free(Pointer snap);

  int libspectrum_snap_read(Pointer snap, byte[] buffer, NativeLong length, int type, String filename);

  int libspectrum_snap_write(PointerByReference buffer, LongByReference length, IntByReference outFlags,
                             Pointer snap, int type, Pointer creator, int inFlags);

  void libspectrum_free(Pointer memory);

  int libspectrum_snap_machine(Pointer snap);

  byte libspectrum_snap_a(Pointer snap);

  byte libspectrum_snap_f(Pointer snap);

  short libspectrum_snap_bc(Pointer snap);

  short libspectrum_snap_de(Pointer snap);

  short libspectrum_snap_hl(Pointer snap);

  byte libspectrum_snap_a_(Pointer snap);

  byte libspectrum_snap_f_(Pointer snap);

  short libspectrum_snap_bc_(Pointer snap);

  short libspectrum_snap_de_(Pointer snap);

  short libspectrum_snap_hl_(Pointer snap);

  short libspectrum_snap_ix(Pointer snap);

  short libspectrum_snap_iy(Pointer snap);

  byte libspectrum_snap_i(Pointer snap);

  byte libspectrum_snap_r(Pointer snap);

  short libspectrum_snap_sp(Pointer snap);

  short libspectrum_snap_pc(Pointer snap);

  short libspectrum_snap_memptr(Pointer snap);

  byte libspectrum_snap_iff1(Pointer snap);

  byte libspectrum_snap_iff2(Pointer snap);

  byte libspectrum_snap_im(Pointer snap);

  int libspectrum_snap_halted(Pointer snap);

  int libspectrum_snap_last_instruction_ei(Pointer snap);

  int libspectrum_snap_tstates(Pointer snap);

  byte libspectrum_snap_out_ula(Pointer snap);

  byte libspectrum_snap_out_128_memoryport(Pointer snap);

  byte libspectrum_snap_out_plus3_memoryport(Pointer snap);

  byte libspectrum_snap_out_ay_registerport(Pointer snap);

  byte libspectrum_snap_ay_registers(Pointer snap, int idx);

  int libspectrum_snap_fuller_box_active(Pointer snap);

  int libspectrum_snap_melodik_active(Pointer snap);

  int libspectrum_snap_issue2(Pointer snap);

  NativeLong libspectrum_snap_joystick_active_count(Pointer snap);

  int libspectrum_snap_joystick_list(Pointer snap, int idx);

  Pointer libspectrum_snap_pages(Pointer snap, int idx);

  int libspectrum_snap_custom_rom(Pointer snap);

  Pointer libspectrum_snap_roms(Pointer snap, int idx);

  NativeLong libspectrum_snap_rom_length(Pointer snap, int idx);

  int libspectrum_snap_zxatasp_active(Pointer snap);

  int libspectrum_snap_zxatasp_upload(Pointer snap);

  int libspectrum_snap_zxatasp_writeprotect(Pointer snap);

  byte libspectrum_snap_zxatasp_port_a(Pointer snap);

  byte libspectrum_snap_zxatasp_port_b(Pointer snap);

  byte libspectrum_snap_zxatasp_port_c(Pointer snap);

  byte libspectrum_snap_zxatasp_control(Pointer snap);

  NativeLong libspectrum_snap_zxatasp_pages(Pointer snap);

  NativeLong libspectrum_snap_zxatasp_current_page(Pointer snap);

  Pointer libspectrum_snap_zxatasp_ram(Pointer snap, int idx);

  int libspectrum_snap_interface2_active(Pointer snap);

  Pointer libspectrum_snap_interface2_rom(Pointer snap, int idx);

  int libspectrum_snap_simpleide_active(Pointer snap);

  int libspectrum_snap_zxmmc_active(Pointer snap);

  int libspectrum_snap_zx_printer_active(Pointer snap);

  int libspectrum_snap_kempston_mouse_active(Pointer snap);

  byte libspectrum_snap_out_scld_hsr(Pointer snap);

  byte libspectrum_snap_out_scld_dec(Pointer snap);

  int libspectrum_snap_beta_active(Pointer snap);

  int libspectrum_snap_beta_paged(Pointer snap);

  int libspectrum_snap_beta_direction(Pointer snap);

  int libspectrum_snap_beta_drive_count(Pointer snap);

  int libspectrum_snap_beta_custom_rom(Pointer snap);

  byte libspectrum_snap_beta_system(Pointer snap);

  byte libspectrum_snap_beta_track(Pointer snap);

  byte libspectrum_snap_beta_sector(Pointer snap);

  byte libspectrum_snap_beta_data(Pointer snap);

  byte libspectrum_snap_beta_status(Pointer snap);

  int libspectrum_snap_interface1_active(Pointer snap);

  int libspectrum_snap_interface1_paged(Pointer snap);

  int libspectrum_snap_interface1_drive_count(Pointer snap);

  int libspectrum_snap_interface1_custom_rom(Pointer snap);

  int libspectrum_snap_uspeech_active(Pointer snap);

  int libspectrum_snap_uspeech_paged(Pointer snap);

  int libspectrum_snap_covox_active(Pointer snap);

  byte libspectrum_snap_covox_dac(Pointer snap);

  int libspectrum_snap_specdrum_active(Pointer snap);

  byte libspectrum_snap_specdrum_dac(Pointer snap);

  int libspectrum_snap_divmmc_active(Pointer snap);

  int libspectrum_snap_divmmc_eprom_writeprotect(Pointer snap);

  int libspectrum_snap_divmmc_paged(Pointer snap);

  byte libspectrum_snap_divmmc_control(Pointer snap);

  NativeLong libspectrum_snap_divmmc_pages(Pointer snap);

  Pointer libspectrum_snap_divmmc_eprom(Pointer snap, int idx);

  Pointer libspectrum_snap_divmmc_ram(Pointer snap, int idx);

  int libspectrum_snap_zxcf_active(Pointer snap);

  int libspectrum_snap_zxcf_upload(Pointer snap);

  byte libspectrum_snap_zxcf_memctl(Pointer snap);

  NativeLong libspectrum_snap_zxcf_pages(Pointer snap);

  Pointer libspectrum_snap_zxcf_ram(Pointer snap, int idx);

  int libspectrum_snap_divide_active(Pointer snap);

  int libspectrum_snap_divide_eprom_writeprotect(Pointer snap);

  NativeLong libspectrum_snap_divide_pages(Pointer snap);

  int libspectrum_snap_divide_paged(Pointer snap);

  byte libspectrum_snap_divide_control(Pointer snap);

  Pointer libspectrum_snap_divide_eprom(Pointer snap, int idx);

  Pointer libspectrum_snap_divide_ram(Pointer snap, int idx);

  int libspectrum_snap_multiface_active(Pointer snap);

  int libspectrum_snap_multiface_model_one(Pointer snap);

  int libspectrum_snap_multiface_disabled(Pointer snap);

  int libspectrum_snap_multiface_software_lockout(Pointer snap);

  int libspectrum_snap_multiface_red_button_disabled(Pointer snap);

  int libspectrum_snap_multiface_paged(Pointer snap);

  Pointer libspectrum_snap_multiface_ram(Pointer snap, int idx);

  NativeLong libspectrum_snap_multiface_ram_length(Pointer snap, int idx);
}
