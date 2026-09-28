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

package com.fpetrola.oozx.speccy.modules.tape;

import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.scheduler.Scheduler;
import com.fpetrola.oozx.speccy.modules.scheduler.Task;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Cassette;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock;
import com.fpetrola.oozx.speccy.modules.tape.cassette.CassetteBlock.*;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Cassettes;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Described;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Sound;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Sounds;
import com.fpetrola.oozx.speccy.modules.tape.cassette.Step;
import com.fpetrola.oozx.speccy.modules.timer.Timer;
import com.fpetrola.oozx.speccy.modules.z80.SpectrumZ80Clock;
import com.fpetrola.oozx.speccy.peripherals.AbstractPeripheral;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The cassette deck: a cassette in it, played block by block onto the EAR line on the machine's
 * clock. What each block sounds like is the block's ({@link Sounds}); what is played after what -
 * pauses, stops, jumps, loops, calls - is the deck's. Which file is which cassette is the tape
 * formats', which arrive as a way in.
 */
@Singleton
public class Tape extends AbstractPeripheral {

    /** Something other than a cassette driving the ear line: a real player on the sound card. */
    public interface EarSource {
        boolean earHigh();
    }

    /** Who is told which block the deck has reached. */
    public interface Listener {
        void blockReached(int block);
    }

    private static final int EAR_OFF = 0xbf;
    private static final int EAR_ON = 0xff;
    private static final int EAR_MASK = 0x40;
    /** What {@link com.fpetrola.oozx.speccy.modules.sound.AudioIn} records at. */
    private static final int SAMPLE_RATE = 44100;

    private final SpectrumZ80Clock clock;
    private final Scheduler scheduler;
    private final Task nextEdge;
    private final TapeSettings settings;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private File file;
    private Cassette cassette;
    private MachineTypes model = MachineTypes.SPECTRUM48K;
    private int earBit = EAR_OFF;
    private boolean playing;
    /** By hand, every stop is obeyed; played by a loader, the first is run past. */
    private boolean byHand;
    /** When the edge being played was due; the next one is measured from it, so nothing drifts. */
    private long edgeAt;
    private EarSource earSource;

    // What is being played, and the state of the blocks that say what is played after what.
    private int block;
    private int sounding;
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

    @Inject
    public Tape(TapeSettings settings, SpectrumZ80Clock clock, Scheduler scheduler, Timer timer) {
        super(List.of());
        timer.loading(this::isTapePlaying);
        this.clock = clock;
        this.scheduler = scheduler;
        this.settings = settings;
        nextEdge = scheduler.register(new Edge());
    }

    /** The deck of that emulator, which a window reaches the way it reaches any device. */
    public static Tape of(Speccy speccy) {
        return (Tape) speccy.peripheralRegistry.find(Tape.class);
    }

    /** Whether some tape format reads this file. */
    public static boolean isATape(String filename) {
        return filename != null && Cassettes.isATape(filename);
    }

    /** A stop-if-48K block asks this. */
    @Override
    public void activate(SpectrumMachine machine) {
        if (machine.snapshotModel() != null) {
            model = machine.snapshotModel();
        }
    }

    @Override
    public boolean fitsOn(SpectrumMachine machine) {
        return true;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void reached(int block) {
        listeners.forEach(listener -> listener.blockReached(block));
    }

    /** The file read into the deck, if a format reads it and nothing is in it already. */
    public boolean insert(File file) {
        if (cassette != null) {
            return false;
        }
        Optional<Cassette> read = Cassettes.read(file);
        if (read.isEmpty()) {
            return false;
        }
        this.file = file;
        cassette = read.get();
        block = 0;
        sound = null;
        stopsPassed = 0;
        calls = null;
        playing = false;
        reached(block);
        return true;
    }

    public boolean eject() {
        if (cassette == null || playing) {
            return false;
        }
        cassette = null;
        file = null;
        block = 0;
        sound = null;
        return true;
    }

    public Optional<Cassette> cassette() {
        return Optional.ofNullable(cassette);
    }

    public File getTapeFilename() {
        return file;
    }

    public boolean isTapePlaying() {
        return playing;
    }

    public int getSelectedBlock() {
        return block;
    }

    public void setSelectedBlock(int block) {
        if (cassette == null || playing || block > blocks().size()) {
            return;
        }
        this.block = block;
        sound = null;
        reached(block);
    }

    public boolean rewind() {
        if (cassette == null || playing) {
            return false;
        }
        block = 0;
        sound = null;
        reached(0);
        return true;
    }

    /** How far the block sounding has gone, 0 to 100. */
    public int progress() {
        if (sound == null || sounding >= blocks().size()) {
            return 0;
        }
        int bytes = Described.of(blocks().get(sounding)).bytes();
        return bytes <= 0 ? 100 : Math.min(100, sound.played() * 100 / bytes);
    }

    /** Plays from the block it is at. */
    public boolean play(boolean byHand) {
        if (cassette == null || playing || block >= blocks().size()) {
            return false;
        }
        this.byHand = byHand;
        playing = true;
        sound = null;
        earBit = rest();
        startEdges();
        edge();
        return true;
    }

    public void stop() {
        if (cassette == null || !playing) {
            return;
        }
        playing = false;
        // Back to a resting line, the microphone put down on stop: the port reads this level
        // whether or not anything is playing, so a tape that stopped halfway through an edge
        // would otherwise leave the line held high for good.
        earBit = EAR_OFF;
        sound = null;
        reached(block);
        scheduler.cancel(nextEdge);
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
            nextEdgeIn(Math.max(1, model.clockFreq / SAMPLE_RATE));
        }
    }

    public boolean isTakingEarFromOutside() {
        return earSource != null;
    }

    public int getEarBit() {
        return earBit;
    }

    /** True while the line is high, which is what makes a load audible. */
    public boolean isEarHigh() {
        return (earBit & EAR_MASK) != 0;
    }

    public void setEarBit(boolean high) {
        earBit = high ? EAR_ON : EAR_OFF;
    }

    private List<CassetteBlock> blocks() {
        return cassette == null ? List.of() : cassette.blocks();
    }

    private int rest() {
        return settings.isInvertedEar() ? EAR_ON : EAR_OFF;
    }

    private void nextEdgeIn(int tstates) {
        scheduler.cancel(nextEdge);
        scheduler.schedule(nextEdge, edgeAt + tstates);
    }

    /** Edges from now on, for a tape that starts playing or a line that starts being listened to. */
    private void startEdges() {
        edgeAt = clock.getTStates();
    }

    /** Plays up to the next edge and asks for the one after: the block's steps, and when it is over the next block, at once. */
    private void edge() {
        if (earSource != null) {
            setEarBit(earSource.earHigh());
            nextEdgeIn(Math.max(1, model.clockFreq / SAMPLE_RATE));
            return;
        }
        while (playing) {
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

    /** The next block that sounds, past the ones that say what is played after what; false when the tape stopped. */
    private boolean nextSound() {
        Deck deck = new Deck();
        while (sound == null) {
            if (block >= blocks().size()) {
                stop();
                return false;
            }
            reached(block);
            sounding = block;
            if (!blocks().get(block).accept(deck)) {
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
            block++;
            return sounds(Sounds.generalized(b, isLast()));
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
                boolean honourStop = byHand || isLast() || stopsPassed++ > 0;
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
            return model.codeModel != MachineTypes.CodeModel.SPECTRUM48K;
        }

        public Boolean signalLevel(SignalLevel b) {
            earBit = b.level() == 0 ? EAR_OFF : EAR_ON;
            return movesOn();
        }

        public Boolean pulseRun(PulseRun b) {
            block++;
            return sounds(Sounds.pulseRun(b));
        }

        public Boolean encodedData(EncodedData b) {
            block++;
            return sounds(Sounds.encodedData(b));
        }

        public Boolean silence(Silence b) {
            block++;
            return sounds(Sounds.silence(b));
        }

        public Boolean pzxInfo(PzxInfo b) { return movesOn(); }
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


    private final class Edge extends Task {
        public void run(long due) {
            edgeAt = due;
            edge();
        }
    }
}
