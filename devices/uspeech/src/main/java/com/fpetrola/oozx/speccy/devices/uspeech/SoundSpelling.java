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

package com.fpetrola.oozx.speccy.devices.uspeech;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A language as people write it, spelled the way the µSpeech wants it: by its sounds, as in he(ll)(oo).
 * <p>
 * The letters go to phonemes by letter-to-sound rules, each a text to match between a left and a right
 * context, tried in order for the letter at hand - for English the Naval Research Laboratory's, for
 * Castilian a few that its regular spelling needs. The phonemes then go to the µSpeech's own letters and
 * bracketed sounds, as its manual names them; a rule may also give those directly, as in (rr).
 */
class SoundSpelling {
  private static final String VOWELS = "AEIOU", VOICED = "BDVGJLMNRWZ", FRONT = "EIY";
  private static final String[] SUFFIXES = {"ING", "ELY", "ER", "ES", "ED", "E"};

  /** Each phoneme of the rules as the µSpeech writes it. */
  private static final Map<String, String> SOUNDS = Map.ofEntries(
      Map.entry("IY", "(ee)"), Map.entry("IH", "i"), Map.entry("EY", "(aa)"), Map.entry("EH", "e"),
      Map.entry("AE", "(eh)"), Map.entry("AA", "a"), Map.entry("AO", "o"), Map.entry("OW", "(oo)"),
      Map.entry("UH", "(uh)"), Map.entry("UW", "(ouu)"), Map.entry("ER", "(er)"), Map.entry("AX", "u"),
      Map.entry("AH", "u"), Map.entry("AY", "(ii)"), Map.entry("AW", "(ow)"), Map.entry("OY", "(oy)"),
      Map.entry("TH", "(th)"), Map.entry("DH", "(dth)"), Map.entry("SH", "(sh)"), Map.entry("ZH", "(zh)"),
      Map.entry("NG", "(ng)"), Map.entry("CH", "(ch)"), Map.entry("WH", "(wh)"));

  private record Rule(String left, String text, String right, String phonemes) {
  }

  private final List<Rule> rules = new ArrayList<>();

  static SoundSpelling english() {
    return new SoundSpelling("nrl-rules.tsv");
  }

  static SoundSpelling castilian() {
    return new SoundSpelling("castellano-rules.tsv");
  }

  private SoundSpelling(String rulesFile) {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(
        SoundSpelling.class.getResourceAsStream(rulesFile), StandardCharsets.UTF_8))) {
      in.lines().filter(line -> !line.startsWith("--")).map(line -> line.split("\t", -1))
          .forEach(part -> rules.add(new Rule(part[0], part[1], part[2], part[3])));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  /** The text spelled by its sounds: words by the rules, a comma or a stop kept as the µSpeech's pauses. */
  String of(String written) {
    String text = " " + plain(written.toUpperCase()).replaceAll("[^A-ZÑÜ',.?!\\- ]", " ") + " ";
    StringBuilder spelled = new StringBuilder();
    for (int at = 1; at < text.length() - 1; ) {
      Rule rule = ruleAt(text, at);
      if (rule == null) {
        at++;
        continue;
      }
      spelled.append(".?!".indexOf(rule.text()) >= 0 ? "." : ",".equals(rule.text()) ? "," : sounds(rule.phonemes()));
      at += rule.text().length();
    }
    return spelled.toString().replaceAll(" +", " ").trim();
  }

  /** Without accents, which the rules do not spell, but keeping Ñ and Ü, which are letters of their own. */
  private static String plain(String upper) {
    String kept = upper.replace('Ñ', '\u0001').replace('Ü', '\u0002');
    return java.text.Normalizer.normalize(kept, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
        .replace('\u0001', 'Ñ').replace('\u0002', 'Ü');
  }

  private Rule ruleAt(String text, int at) {
    for (Rule rule : rules) {
      if (text.startsWith(rule.text(), at) && left(text, at - 1, rule.left(), rule.left().length() - 1)
          && right(text, at + rule.text().length(), rule.right(), 0)) {
        return rule;
      }
    }
    return null;
  }

  private static String sounds(String phonemes) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < phonemes.length(); ) {
      String two = i + 2 <= phonemes.length() ? phonemes.substring(i, i + 2) : "";
      if (SOUNDS.containsKey(two)) {
        out.append(SOUNDS.get(two));
        i += 2;
      } else {
        out.append(phonemes.charAt(i++));
      }
    }
    return out.toString();
  }

  /** Whether the pattern, read from its end backwards, matches the text leftwards from that position. */
  private static boolean left(String text, int at, String pattern, int p) {
    if (p < 0) return true;
    char want = pattern.charAt(p);
    return switch (want) {
      case '#' -> at >= 0 && is(VOWELS, text.charAt(at)) && vowelsLeft(text, at, pattern, p);
      case ':' -> left(text, at, pattern, p - 1) || at >= 0 && consonant(text.charAt(at)) && left(text, at - 1, pattern, p);
      default -> at >= 0 && one(want, text.charAt(at)) && left(text, at - 1, pattern, p - 1);
    };
  }

  private static boolean vowelsLeft(String text, int at, String pattern, int p) {
    int from = at;
    while (from >= 0 && is(VOWELS, text.charAt(from))) from--;
    return left(text, from, pattern, p - 1);
  }

  /** Whether the pattern, read from its start, matches the text rightwards from that position. */
  private static boolean right(String text, int at, String pattern, int p) {
    if (p == pattern.length()) return true;
    char want = pattern.charAt(p);
    return switch (want) {
      case '#' -> at < text.length() && is(VOWELS, text.charAt(at)) && vowelsRight(text, at, pattern, p);
      case ':' -> right(text, at, pattern, p + 1) || at < text.length() && consonant(text.charAt(at)) && right(text, at + 1, pattern, p);
      case '%' -> java.util.Arrays.stream(SUFFIXES).anyMatch(s -> text.startsWith(s, at) && right(text, at + s.length(), pattern, p + 1));
      default -> at < text.length() && one(want, text.charAt(at)) && right(text, at + 1, pattern, p + 1);
    };
  }

  private static boolean vowelsRight(String text, int at, String pattern, int p) {
    int to = at;
    while (to < text.length() && is(VOWELS, text.charAt(to))) to++;
    return right(text, to, pattern, p + 1);
  }

  /** One character of a context: a letter itself, a word's edge, or one of the classes that stand for one. */
  private static boolean one(char want, char got) {
    return switch (want) {
      case ' ' -> !Character.isLetter(got);
      case '^' -> consonant(got);
      case '.' -> is(VOICED, got);
      case '+' -> is(FRONT, got);
      default -> want == got;
    };
  }

  private static boolean consonant(char c) {
    return Character.isLetter(c) && !is(VOWELS, c);
  }

  private static boolean is(String set, char c) {
    return set.indexOf(c) >= 0;
  }
}
