package com.fpetrola.oozx.speccy.devices.bettersound;

import java.util.Arrays;

/**
 * One frame of the game's speaker, as the better sound hears it: every OUT to the ULA's port in the order the
 * game made them, with its time in T-states from the frame's start, the byte written, the address of the
 * instruction that wrote it and where its routine returns to. Nothing in it names a game: the routine that plays a
 * sound is told apart by its pc and by who called it.
 *
 * <p>A stage that turns some of these OUTs into something else claims them, and the beeper does not play a claimed
 * one. The viewer hands a frame to the stages one frame before it mixes it (PLAN-SONIDO.md, D2), so a run of edges
 * that starts at the end of a frame is decided with what follows it.
 *
 * <p>Reused frame after frame by the viewer, which fills it: a stage that wants to keep something copies it.
 */
public final class FrameSound {
  private int[] t = new int[256], value = new int[256], pc = new int[256], caller = new int[256];
  private boolean[] claimed = new boolean[256];
  private int size, length, mixed;
  private long index;

  /** the frame's number since the sound started */
  public long index() {
    return index;
  }

  /** how many OUTs the frame has */
  public int size() {
    return size;
  }

  /** the {@code i}-th OUT's time, in T-states from the frame's start (it may fall past {@link #length()}) */
  public int t(int i) {
    return t[i];
  }

  /** the byte the {@code i}-th OUT wrote: bit 4 is the speaker, bit 3 the MIC, bits 0-2 the border */
  public int value(int i) {
    return value[i];
  }

  /** the address of the instruction that made the {@code i}-th OUT, or -1 if the replay did not say */
  public int pc(int i) {
    return pc[i];
  }

  /**
   * where the routine of the {@code i}-th OUT returns to, or -1 if the replay did not say: one routine can play every
   * sound of a game, and then only the place that called it tells them apart (Dynamite Dan's does)
   */
  public int caller(int i) {
    return caller[i];
  }

  /** the speaker's level after the {@code i}-th OUT */
  public boolean speaker(int i) {
    return (value[i] & 0x10) != 0;
  }

  /** how long the frame ran, in T-states: the machine's 69888, or more with interrupts off */
  public int length() {
    return length;
  }

  /**
   * How many T-states of it the viewer mixes: {@link #length()}, or the machine's 69888 when the viewer pins a long
   * frame to it (its edges past it pile on the last T-state, {@code sound.frames.whole}). A stage that plays the frame
   * plays this many, or its sound and the beeper's drift apart.
   */
  public int mixed() {
    return mixed;
  }

  void mixed(int mixed) {
    this.mixed = mixed;
  }

  /** the {@code i}-th OUT becomes the stage's: the beeper does not play it */
  public void claim(int i) {
    claimed[i] = true;
  }

  public boolean claimed(int i) {
    return claimed[i];
  }

  void reset(long index) {
    Arrays.fill(claimed, 0, size, false);
    this.index = index;
    size = 0;
    length = 0;
    mixed = 0;
  }

  void add(int t, int value, int pc) {
    add(t, value, pc, -1);
  }

  void add(int t, int value, int pc, int caller) {
    if (size == this.t.length) {
      int n = size * 2;
      this.t = Arrays.copyOf(this.t, n);
      this.value = Arrays.copyOf(this.value, n);
      this.pc = Arrays.copyOf(this.pc, n);
      this.caller = Arrays.copyOf(this.caller, n);
      claimed = Arrays.copyOf(claimed, n);
    }
    this.t[size] = t;
    this.value[size] = value;
    this.pc[size] = pc;
    this.caller[size] = caller;
    size++;
  }

  void close(int length) {
    this.length = length;
    this.mixed = length;
  }
}
