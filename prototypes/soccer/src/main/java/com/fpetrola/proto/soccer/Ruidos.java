package com.fpetrola.proto.soccer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Los ruidos de la pelota, sintetizados al arrancar (no hay archivos de sonido): un golpe seco al
 * patearla, un toc de vidrio contra la pared, un clang metálico contra el palo y uno sordo contra un
 * jugador. Suenan más fuerte cuanto más fuerte fue el golpe. Si no hay audio, no suena nada.
 */
final class Ruidos implements Match.Ears, Disposable {
    private static final int RATE = 44100;
    /** Con cuánta fuerza (m/s) suena cada uno al máximo, y cada cuánto puede volver a sonar (s). */
    private static final float GAP = 0.05f;

    private final Map<Match.Noise, Sound> sounds = new EnumMap<>(Match.Noise.class);
    private final Map<Match.Noise, Float> full = new EnumMap<>(Match.Noise.class);
    private final Map<Match.Noise, Long> last = new EnumMap<>(Match.Noise.class);

    Ruidos() {
        full.put(Match.Noise.KICK, 28f);
        full.put(Match.Noise.POST, 14f);
        full.put(Match.Noise.WALL, 14f);
        full.put(Match.Noise.BODY, 10f);
        try {
            sounds.put(Match.Noise.KICK, load("patada", kick()));
            sounds.put(Match.Noise.POST, load("palo", post()));
            sounds.put(Match.Noise.WALL, load("vidrio", glass()));
            sounds.put(Match.Noise.BODY, load("cuerpo", body()));
        } catch (RuntimeException e) {
            Gdx.app.log("Ruidos", "sin sonido: " + e.getMessage());
        }
    }

    @Override
    public void hear(Match.Noise noise, float strength) {
        Sound s = sounds.get(noise);
        if (s == null) return;
        long now = System.nanoTime();
        Long before = last.get(noise);
        if (before != null && now - before < GAP * 1e9) return;
        last.put(noise, now);
        float volume = MathUtils.clamp(strength / full.get(noise), 0.08f, 1f);
        s.play(volume, MathUtils.random(0.94f, 1.06f), 0);
    }

    @Override
    public void dispose() {
        for (Sound s : sounds.values()) s.dispose();
    }

    // ---- los sonidos ------------------------------------------------------------------------------

    /** La patada: un golpe grave que se apaga enseguida, con un chasquido al principio. */
    private static float[] kick() {
        float[] out = new float[(int) (RATE * 0.16f)];
        Random r = new Random(1);
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / RATE;
            float thump = (float) Math.sin(2 * Math.PI * (110 - 300 * t) * t) * (float) Math.exp(-t * 28);
            float click = (r.nextFloat() * 2 - 1) * (float) Math.exp(-t * 300);
            out[i] = 0.9f * thump + 0.35f * click;
        }
        return out;
    }

    /** El palo: varios tonos que no son armónicos entre sí y duran, como un metal. */
    private static float[] post() {
        float[] out = new float[(int) (RATE * 0.9f)];
        float[] freq = {523, 1312, 2117, 3010}, gain = {0.5f, 0.3f, 0.2f, 0.1f}, decay = {5, 7, 10, 14};
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / RATE, v = 0;
            for (int k = 0; k < freq.length; k++) v += gain[k] * (float) Math.sin(2 * Math.PI * freq[k] * t) * (float) Math.exp(-t * decay[k]);
            out[i] = v * Math.min(1, t * 2000);
        }
        return out;
    }

    /** El vidrio: un toc corto y agudo, con un poco de brillo. */
    private static float[] glass() {
        float[] out = new float[(int) (RATE * 0.25f)];
        Random r = new Random(2);
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / RATE;
            float knock = (float) Math.sin(2 * Math.PI * 180 * t) * (float) Math.exp(-t * 40);
            float ring = 0.35f * (float) Math.sin(2 * Math.PI * 2400 * t) * (float) Math.exp(-t * 18)
                    + 0.2f * (float) Math.sin(2 * Math.PI * 3700 * t) * (float) Math.exp(-t * 25);
            float tick = (r.nextFloat() * 2 - 1) * (float) Math.exp(-t * 400);
            out[i] = 0.7f * knock + ring + 0.3f * tick;
        }
        return out;
    }

    /** Contra un jugador: un golpe sordo, apagado. */
    private static float[] body() {
        float[] out = new float[(int) (RATE * 0.12f)];
        Random r = new Random(3);
        float low = 0;
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / RATE;
            low += 0.08f * ((r.nextFloat() * 2 - 1) - low);  // ruido filtrado: apagado
            out[i] = (0.8f * (float) Math.sin(2 * Math.PI * 140 * t) + 2.5f * low) * (float) Math.exp(-t * 35);
        }
        return out;
    }

    // ---- de muestras a un sonido de libGDX ----------------------------------------------------

    /** Escribe las muestras como un WAV de 16 bits en un archivo temporal y lo carga. */
    private static Sound load(String name, float[] samples) {
        float peak = 1e-6f;
        for (float s : samples) peak = Math.max(peak, Math.abs(s));
        byte[] wav = new byte[44 + samples.length * 2];
        header(wav, samples.length * 2);
        for (int i = 0; i < samples.length; i++) {
            int v = Math.round(samples[i] / peak * 0.9f * Short.MAX_VALUE);
            wav[44 + i * 2] = (byte) v;
            wav[45 + i * 2] = (byte) (v >> 8);
        }
        try {
            // libGDX reconoce el formato por la extensión: tiene que terminar en .wav
            java.io.File file = java.io.File.createTempFile("soccer-" + name + "-", ".wav");
            file.deleteOnExit();
            FileHandle f = Gdx.files.absolute(file.getAbsolutePath());
            f.writeBytes(wav, false);
            return Gdx.audio.newSound(f);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void header(byte[] b, int data) {
        put(b, 0, "RIFF");
        int32(b, 4, 36 + data);
        put(b, 8, "WAVEfmt ");
        int32(b, 16, 16);
        int16(b, 20, 1);
        int16(b, 22, 1);
        int32(b, 24, RATE);
        int32(b, 28, RATE * 2);
        int16(b, 32, 2);
        int16(b, 34, 16);
        put(b, 36, "data");
        int32(b, 40, data);
    }

    private static void put(byte[] b, int at, String s) {
        for (int i = 0; i < s.length(); i++) b[at + i] = (byte) s.charAt(i);
    }

    private static void int32(byte[] b, int at, int v) {
        for (int i = 0; i < 4; i++) b[at + i] = (byte) (v >> (8 * i));
    }

    private static void int16(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
    }
}
