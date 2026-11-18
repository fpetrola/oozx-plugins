package com.fpetrola.oozx.speccy.devices.uspeech;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SoundSpellingTest {
  private final SoundSpelling spelling = SoundSpelling.english();

  @Test
  void wordsComeOutAsTheirSoundsTheWayTheManualSpellsThem() {
    assertEquals("f(ii)v", spelling.of("five"));
    assertEquals("(dth)en", spelling.of("then"));
    assertEquals("sp(ee)k", spelling.of("speak"));
    assertEquals("stri(ng)", spelling.of("string"));
    assertEquals("s(aa)v", spelling.of("save"));
    assertEquals("jumps", spelling.of("jumps"), "u before two consonants is the u of but");
  }

  @Test
  void wordsArePartedBySpacesAndPhrasesByTheirPunctuation() {
    assertEquals("hel(oo) w(er)ld.", spelling.of("Hello world!"));
  }

  @Test
  void castilianIsSpelledByTheSoundsOfItsLettersAsTheRiverPlateSaysThem() {
    SoundSpelling castilian = SoundSpelling.castilian();
    assertEquals("ola", castilian.of("hola"), "h is silent");
    assertEquals("ka(sh)e", castilian.of("calle"), "ll is sh");
    assertEquals("s(ee)elo keso", castilian.of("cielo queso"), "c before i is s, qu before e is k");
    assertEquals("(rr)aton pero pe(rr)o", castilian.of("ratón pero perro"), "r strong at the start and doubled");
    assertEquals("hente ge(rr)a hamas", castilian.of("gente guerra jamás"), "g before e and j are h, gu before e is g");
    assertEquals("o(ee) (ee) manyana", castilian.of("hoy y mañana"), "y at the end and alone is i, ñ is ny");
    assertEquals("nyand(ouu), p(ee)ngw(ee)no.", castilian.of("Ñandú, pingüino."), "ü is w; accents go, pauses stay");
  }
}
