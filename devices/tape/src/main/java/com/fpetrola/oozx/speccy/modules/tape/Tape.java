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
/*
 * A Turbo Speed Data block's polarity matters to a few loaders - MASK and Basil the Great Mouse
 * Detective need it one way, and The Edge's own protection (Starbike, Brian Bloodaxe, That's the
 * Spirit) needs it the other; there is no setting here that satisfies both.
 */
package com.fpetrola.oozx.speccy.modules.tape;

import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.plugins.Plugins;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.scheduler.Scheduler;
import com.fpetrola.oozx.speccy.modules.scheduler.Task;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Cassette;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock.*;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Sound;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Sounds;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Step;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeFormat;
import com.fpetrola.oozx.speccy.modules.tape.cassette.TapeRefused;
import com.fpetrola.oozx.speccy.modules.timer.Timer;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.oozx.speccy.peripherals.AbstractPeripheral;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.DeflaterOutputStream;

/**
 * The cassette deck: a cassette in it, read by whichever tape format answers for the file, played
 * block by block onto the EAR line on the machine's clock, and recorded from the MIC line. What
 * each block sounds like is the block's ({@link Sounds}); what is played after what - pauses,
 * stops, jumps, loops, calls - is the deck's.
 */
@Singleton
public class Tape extends AbstractPeripheral {

    public enum TapeState {
        EJECT, INSERT, STOP, PLAY, RECORD
    }

    /**
     * Something other than a file driving the ear line: a real cassette player on the sound card,
     * asked once per sound-card sample.
     */
    public interface EarSource {
        boolean earHigh();
    }

    private static final int EAR_OFF = 0xbf;
    private static final int EAR_ON = 0xff;
    private static final int EAR_MASK = 0x40;
    /** What {@link com.fpetrola.oozx.speccy.modules.sound.AudioIn} records at. */
    private static final int SAMPLE_RATE = 44100;
    private static final String TZX_SIGNATURE = "ZXTape!\u001A";
    private static final String TZX_CREATOR = "TZX created with JSpeccy v0.95";

    private final SpectrumZ80Clock clock;
    private final Scheduler scheduler;
    private final Task nextEdge;
    private final TapeSettingsType settings;
    private final List<TapeStateListener> stateListeners = new ArrayList<>();
    private final List<TapeBlockListener> blockListeners = new ArrayList<>();
    private final Log1 log = new Log1();

    private File filename;
    private Cassette cassette;
    /** Where each block sits in the file, for whoever shows how far a block has played. */
    private List<TapeBlock> placed = List.of();
    private MachineTypes spectrumModel = MachineTypes.SPECTRUM48K;
    private int earBit = EAR_OFF;
    private boolean tapePlaying;
    private boolean tapeRecording;
    private boolean manualMode;
    /** When the edge being played was due; the next one is measured from it, so nothing drifts. */
    private long edgeAt;
    private EarSource earSource;

    // What is being played, and the state of the blocks that say what is played after what.
    private int block;
    private Sound sound;
    /** Whether the block sounding moves on when it is over, as a TAP's does, rather than when it starts. */
    private boolean movesOnWhenOver;
    /** How many "stop the tape" blocks an automatic load has already run past. */
    private int stopsPassed;
    private int loopsLeft;
    private int loopStart;
    private int[] calls;
    private int callsMade;
    private int callFrom;

    // Recording.
    private ByteArrayOutputStream record;
    private DeflaterOutputStream packed;
    private long timeLastOut;
    private boolean micBit;
    private int freqSample;
    private float cswStatesSample;
    private int cswPulses;
    private int bitsLastByte;
    private byte byteTmp;

    @Inject
    public Tape(TapeSettingsType tapeSettings, SpectrumZ80Clock aClock, Scheduler scheduler, Timer timer) {
        super(java.util.List.of());
        timer.loading(this::isTapePlaying);
        clock = aClock;
        this.scheduler = scheduler;
        nextEdge = scheduler.register(new Edge());
        settings = tapeSettings;
    }

    public void addTapeChangedListener(final TapeStateListener listener) {
        Objects.requireNonNull(listener, "Internal error: tape change listener can't be null");
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
        }
    }

    public void removeTapeChangedListener(final TapeStateListener listener) {
        Objects.requireNonNull(listener, "Internal error: tape change listener can't be null");
        if (!stateListeners.remove(listener)) {
            throw new IllegalArgumentException("Internal error: Listener was not listening on object");
        }
    }

    private void fireTapeStateChanged(final TapeState state) {
        stateListeners.forEach(listener -> listener.stateChanged(state));
    }

    public void addTapeBlockListener(final TapeBlockListener listener) {
        Objects.requireNonNull(listener, "Internal error: tape block listener can't be null");
        if (!blockListeners.contains(listener)) {
            blockListeners.add(listener);
        }
    }

    public void removeTapeBlockListener(final TapeBlockListener listener) {
        Objects.requireNonNull(listener, "Internal error: tape block listener can't be null");
        if (!blockListeners.remove(listener)) {
            throw new IllegalArgumentException("Internal error: tape block listener was not listening on object");
        }
    }

    private void fireTapeBlockChanged(final int block) {
        blockListeners.forEach(listener -> listener.blockChanged(block));
    }

    public void setSpectrumModel(MachineTypes model) {
        spectrumModel = model;
    }

    /**
     * Byte the player has reached in the tape image. A read-only view for a progress display;
     * the caller knows where each block starts and ends and works the rest out from that.
     */
    public int getTapePosition() {
        if (block >= placed.size()) {
            return 0;
        }
        int at = movesOnWhenOver || sound == null ? block : Math.max(0, block - 1);
        TapeBlock where = placed.get(Math.min(at, placed.size() - 1));
        return sound == null ? where.start() : Math.min(where.end(), where.start() + sound.played());
    }

    public int getSelectedBlock() {
        return block;
    }

    public void setSelectedBlock(int block) {
        if (cassette == null || isTapePlaying() || block > blocks().size()) {
            return;
        }
        this.block = block;
        sound = null;
        fireTapeBlockChanged(block);
    }

    public boolean insert(File fileName) {
        if (cassette != null) {
            return false;
        }
        try {
            return insert(fileName, fileName.getName(), Files.readAllBytes(fileName.toPath()), 0);
        } catch (IOException cannotRead) {
            log.error("IOexception: ", cannotRead);
            return false;
        }
    }

    public boolean insertEmbeddedTape(String fileName, String extension, byte[] tapeData, int selectedBlock) {
        if (cassette != null) {
            return false;
        }
        return insert(new File(fileName), "tape." + extension, tapeData, selectedBlock);
    }

    /** The file read by whichever format answers for its name, into the deck. */
    private boolean insert(File file, String name, byte[] bytes, int selectedBlock) {
        Optional<TapeFormat> format = Plugins.found(TapeFormat.class).stream().filter(one -> one.reads(new File(name))).findFirst();
        if (format.isEmpty()) {
            return false;
        }
        try {
            cassette = format.get().read(bytes);
        } catch (TapeRefused refused) {
            log.info("Not a tape: " + refused.getMessage());
            return false;
        }
        filename = file;
        placed = TapeBlock.read(name, bytes);
        block = Math.max(0, Math.min(selectedBlock, blocks().size()));
        sound = null;
        stopsPassed = 0;
        calls = null;
        tapePlaying = tapeRecording = false;
        fireTapeStateChanged(TapeState.INSERT);
        fireTapeBlockChanged(block);
        return true;
    }

    public boolean eject() {
        if (cassette == null || tapePlaying || tapeRecording) {
            return false;
        }
        cassette = null;
        placed = List.of();
        filename = null;
        block = 0;
        sound = null;
        fireTapeStateChanged(TapeState.EJECT);
        return true;
    }

    /**
     * Takes the ear level from outside, or stops taking it when given null. Faster than once per
     * sound-card sample would read the same sample twice and slower would step over edges.
     */
    public void takeEarFrom(EarSource source) {
        if (earSource != null) {
            scheduler.cancel(nextEdge);
        }
        earSource = source;
        if (source != null) {
            startEdges();
            nextEdgeIn(tstatesPerSample());
        }
    }

    public boolean isTakingEarFromOutside() {
        return earSource != null;
    }

    private void nextEdgeIn(int tstates) {
        scheduler.cancel(nextEdge);
        scheduler.schedule(nextEdge, edgeAt + tstates);
    }

    /** Edges from now on, for a tape that starts playing or a line that starts being listened to. */
    private void startEdges() {
        edgeAt = clock.getTStates();
    }

    private int tstatesPerSample() {
        return Math.max(1, spectrumModel.clockFreq / SAMPLE_RATE);
    }

    public int getEarBit() {
        return earBit;
    }

    /** True while the tape is feeding a high level, which is what makes a load audible. */
    public boolean isEarHigh() {
        return (earBit & EAR_MASK) != 0;
    }

    public void setEarBit(boolean earValue) {
        earBit = earValue ? EAR_ON : EAR_OFF;
    }

    public boolean isTapePlaying() {
        return tapePlaying;
    }

    public boolean fitsOn(SpectrumMachine machine) {
        return true;
    }

    /** Whether this file is a cassette at all, which only the deck's own formats decide. */
    public static boolean isATape(String filename) {
        if (filename == null) {
            return false;
        }
        String name = filename.toLowerCase();
        return name.endsWith(".tap") || name.endsWith(".tzx") || name.endsWith(".csw");
    }

    /** The deck of that emulator, which a window reaches the way it reaches any device. */
    public static Tape of(Speccy speccy) {
        return (Tape) speccy.peripheralRegistry.find(Tape.class);
    }

    public boolean isTapeRecording() {
        return tapeRecording;
    }

    public boolean isTapeRunning() {
        return tapePlaying || tapeRecording;
    }

    public boolean isTapeInserted() {
        return cassette != null;
    }

    public boolean isTapeReady() {
        return cassette != null && !tapePlaying && !tapeRecording;
    }

    public File getTapeFilename() {
        return filename;
    }

    /** Plays from the block it is at; by hand, it stops at every stop, and never runs past one. */
    public boolean play(boolean origin) {
        if (cassette == null || tapePlaying || tapeRecording || block >= blocks().size()) {
            return false;
        }
        manualMode = origin;
        fireTapeStateChanged(TapeState.PLAY);
        tapePlaying = true;
        sound = null;
        earBit = rest();
        startEdges();
        edge();
        return true;
    }

    public void stop() {
        if (cassette == null || !tapePlaying || tapeRecording) {
            return;
        }
        tapePlaying = false;
        // Back to a resting line, the microphone put down on stop: the port reads this level
        // whether or not anything is playing, so a tape that stopped halfway through an edge
        // would otherwise leave the line held high for good.
        earBit = EAR_OFF;
        if (sound != null && movesOnWhenOver) {
            sound = null;
        } else {
            sound = null;
        }
        fireTapeBlockChanged(block);
        fireTapeStateChanged(TapeState.STOP);
        scheduler.cancel(nextEdge);
    }

    public boolean rewind() {
        if (cassette == null || tapePlaying || tapeRecording) {
            return false;
        }
        block = 0;
        sound = null;
        fireTapeBlockChanged(0);
        return true;
    }

    private List<CassetteBlock> blocks() {
        return cassette == null ? List.of() : cassette.blocks();
    }

    private int rest() {
        return settings.isInvertedEar() ? EAR_ON : EAR_OFF;
    }

    /**
     * Plays up to the next edge of the signal, and asks for the one after: the steps of the block
     * sounding, and when it is over the next block, at the same moment.
     */
    private void edge() {
        if (earSource != null) {
            setEarBit(earSource.earHigh());
            nextEdgeIn(tstatesPerSample());
            return;
        }
        while (tapePlaying) {
            if (sound == null && !nextSound()) {
                return;
            }
            Step step = sound.next();
            if (step == null) {
                sound = null;
                if (movesOnWhenOver) {
                    block++;
                    movesOnWhenOver = false;
                }
                continue;
            }
            level(step.level());
            if (step.waits()) {
                nextEdgeIn(step.tstates());
                return;
            }
        }
    }

    private void level(Step.Level level) {
        switch (level) {
            case TOGGLE -> earBit ^= EAR_MASK;
            case HIGH -> earBit = EAR_ON;
            case LOW -> earBit = EAR_OFF;
            case REST -> earBit = rest();
            case KEEP -> {
            }
        }
    }

    /**
     * The next block that sounds, past the ones that say what is played after what; false when
     * the tape stopped, at its end or at a stop.
     */
    private boolean nextSound() {
        Deck deck = new Deck();
        while (sound == null) {
            if (block >= blocks().size()) {
                stop();
                return false;
            }
            fireTapeBlockChanged(block);
            CassetteBlock next = blocks().get(block);
            if (!next.accept(deck)) {
                stop();
                return false;
            }
        }
        return true;
    }

    /** Whether the block being moved past is the tape's last, whose pause is never long nor none. */
    private boolean isLast() {
        return block >= blocks().size();
    }

    /**
     * What each block does to what is played: a block that sounds becomes the sound, one that
     * says what comes next moves the deck there. False is a stop.
     */
    private final class Deck implements CassetteBlock.Visitor<Boolean> {

        private boolean sounds(Sound next) {
            sound = next;
            return true;
        }

        private boolean movesOn() {
            block++;
            return true;
        }

        public Boolean tapData(TapData b) {
            movesOnWhenOver = true;
            return sounds(Sounds.tap(b));
        }

        public Boolean standardData(StandardData b) {
            block++;
            return sounds(Sounds.data(Sounds.PILOT, Sounds.pilotPulsesOf(b.data()), Sounds.SYNC1, Sounds.SYNC2,
                Sounds.ZERO, Sounds.ONE, 8, Sounds.pause(b.pause(), isLast()), b.data()));
        }

        public Boolean turboData(TurboData b) {
            block++;
            return sounds(Sounds.data(b.pilot(), b.pilotPulses(), b.sync1(), b.sync2(), b.zero(), b.one(), b.usedBits(),
                Sounds.pause(b.pause(), isLast()), b.data()));
        }

        public Boolean pureTone(PureTone b) {
            block++;
            return sounds(Sounds.tone(b));
        }

        public Boolean pulseSequence(PulseSequence b) {
            block++;
            return sounds(Sounds.pulses(b));
        }

        public Boolean pureData(PureData b) {
            block++;
            return sounds(Sounds.pureData(b, Sounds.pause(b.pause(), isLast())));
        }

        public Boolean directRecording(DirectRecording b) {
            block++;
            return sounds(Sounds.direct(b, Sounds.pause(b.pause(), isLast())));
        }

        public Boolean cswRecording(CswRecording b) {
            block++;
            return sounds(Sounds.csw(b, Sounds.pause(b.pause(), isLast())));
        }

        public Boolean cswTape(CswTape b) {
            block++;
            return sounds(Sounds.cswTape(b));
        }

        public Boolean generalizedData(GeneralizedData b) {
            log.info("Gen. Data Block not supported!. Skipping...");
            return movesOn();
        }

        /**
         * A pause, or with none the "stop the tape" command, which expects a person to press play
         * again when the game asks for more. A loader mid-load resets the machine within the same
         * tick the signal stops, long before anything outside the deck could restart it, so the
         * first stop an automatic load meets is run past - Renegade ends its Speedlock that way,
         * with one block still to come - and the ones after it are obeyed. A person driving the
         * deck by hand always decides.
         */
        public Boolean pause(Pause b) {
            block++;
            if (b.milliseconds() == 0) {
                boolean honourStop = manualMode || isLast() || stopsPassed++ > 0;
                return !honourStop;
            }
            return sounds(Sounds.silence(b.milliseconds() * (Sounds.SECOND / 1000)));
        }

        public Boolean jump(Jump b) {
            block += b.offset();
            return true;
        }

        public Boolean loopStart(LoopStart b) {
            loopsLeft = b.repetitions();
            loopStart = ++block;
            return true;
        }

        public Boolean loopEnd(LoopEnd b) {
            if (--loopsLeft == 0) {
                block++;
            } else {
                block = loopStart;
            }
            return true;
        }

        public Boolean callSequence(CallSequence b) {
            if (calls != null || b.offsets().length == 0) {
                log.info("The CALL blocks can't be nested!. Skipping!!!");
                return movesOn();
            }
            calls = b.offsets();
            callFrom = block;
            callsMade = 0;
            block += calls[callsMade++];
            return true;
        }

        public Boolean returnFromSequence(ReturnFromSequence b) {
            if (calls == null) {
                return movesOn();
            }
            if (callsMade < calls.length) {
                block = callFrom + calls[callsMade++];
            } else {
                block = callFrom + 1;
                calls = null;
            }
            return true;
        }

        public Boolean stopIf48K(StopIf48K b) {
            block++;
            return spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUM48K;
        }

        public Boolean signalLevel(SignalLevel b) {
            earBit = b.level() == 0 ? EAR_OFF : EAR_ON;
            return movesOn();
        }

        public Boolean groupStart(GroupStart b) { return movesOn(); }
        public Boolean groupEnd(GroupEnd b) { return movesOn(); }
        public Boolean select(Select b) { return movesOn(); }
        public Boolean text(Text b) { return movesOn(); }
        public Boolean message(Message b) { return movesOn(); }
        public Boolean archiveInfo(ArchiveInfo b) { return movesOn(); }
        public Boolean hardwareInfo(HardwareInfo b) { return movesOn(); }
        public Boolean customInfo(CustomInfo b) { return movesOn(); }
        public Boolean glue(Glue b) { return movesOn(); }
    }

    public boolean startRecording() {
        if (!isTapeReady() || !filename.getName().toLowerCase().endsWith(".tzx")) {
            return false;
        }
        record = new ByteArrayOutputStream();
        timeLastOut = 0;
        tapeRecording = true;
        if (settings.isHighSamplingFreq()) {
            freqSample = 48000;
            cswStatesSample = 3500000.0f / freqSample;
            cswPulses = 0;
            packed = new DeflaterOutputStream(record);
        } else {
            freqSample = 79; // 44.1 Khz
        }
        fireTapeStateChanged(TapeState.RECORD);
        return true;
    }

    public boolean stopRecording() {
        if (!tapeRecording) {
            return false;
        }
        try (BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(filename, true))) {
            if (blocks().isEmpty()) {
                fOut.write(TZX_SIGNATURE.getBytes("US-ASCII"));
                fOut.write(01);
                fOut.write(20);
                byte idTZX[] = TZX_CREATOR.getBytes("US-ASCII");
                fOut.write(0x30);
                fOut.write(idTZX.length);
                fOut.write(idTZX);
            }
            if (settings.isHighSamplingFreq()) {
                packed.close();
                record.close();
                fOut.write(0x18); // TZX ID: CSW Recording
                fOut.write(record.size() + 10);
                fOut.write((record.size() + 10) >>> 8);
                fOut.write((record.size() + 10) >>> 16);
                fOut.write((record.size() + 10) >>> 24);
                fOut.write(0x00);
                fOut.write(0x00); // 0 sec end block pause
                fOut.write(freqSample);
                fOut.write(freqSample >>> 8);
                fOut.write(freqSample >>> 16);
                fOut.write(0x02); // Z-RLE encoding
                fOut.write(cswPulses);
                fOut.write(cswPulses >>> 8);
                fOut.write(cswPulses >>> 16);
                fOut.write(cswPulses >>> 24);
                record.writeTo(fOut);
            } else {
                if (bitsLastByte != 0) {
                    byteTmp <<= (8 - bitsLastByte);
                    record.write(byteTmp);
                }
                fOut.write(0x15); // TZX ID: Direct Recording Block
                fOut.write(freqSample);
                fOut.write(0x00); // T-states per sample
                fOut.write(0x00);
                fOut.write(0x00); // 0 sec end block pause
                fOut.write(bitsLastByte);
                fOut.write(record.size());
                fOut.write(record.size() >>> 8);
                fOut.write(record.size() >>> 16);
                record.close();
                record.writeTo(fOut);
            }
        } catch (final IOException ex) {
            log.error("IOException: ", ex);
        }
        tapeRecording = false;
        fireTapeStateChanged(TapeState.STOP);
        File tmp = filename;
        eject();
        insert(tmp);
        return true;
    }

    public void recordPulse(boolean micState) {
        if (timeLastOut == 0) {
            timeLastOut = clock.getAbsTstates();
            micBit = micState;
            return;
        }
        int len = (int) (clock.getAbsTstates() - timeLastOut);
        if (settings.isHighSamplingFreq()) { // CSW
            cswPulses++;
            int pulses = (int) ((len / cswStatesSample) + 0.49f);
            try {
                if (pulses > 255) {
                    packed.write(0);
                    packed.write(pulses);
                    packed.write(pulses >>> 8);
                    packed.write(pulses >>> 16);
                    packed.write(pulses >>> 24);
                } else {
                    packed.write(pulses);
                }
            } catch (final IOException ex) {
                log.error("IOException: ", ex);
            }
        } else { // DRB
            int pulses = len + (freqSample >>> 1);
            pulses /= freqSample;
            while (pulses-- > 0) {
                if (bitsLastByte == 8) {
                    record.write(byteTmp);
                    bitsLastByte = 0;
                    byteTmp = 0;
                }
                byteTmp <<= 1;
                if (micBit) {
                    byteTmp |= 0x01;
                }
                bitsLastByte++;
            }
        }
        timeLastOut = clock.getAbsTstates();
        micBit = micState;
    }

    private final class Edge extends Task {
        public void run(long due) {
            edgeAt = due;
            edge();
        }
    }
}
