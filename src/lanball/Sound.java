package lanball;
import java.util.Random;
import javax.sound.sampled.*;

/*
 * Every sound is made from sine waves and noise at start-up: no audio files, nothing to license.
 * Each sound keeps three clips, so quick repeats overlap instead of cutting off. If audio fails, the game stays silent.
 */
public class Sound {
    static final float RATE = 22050;
    static final Sound KICK = new Sound(0, .09), BOUNCE = new Sound(1, .06), WHISTLE = new Sound(2, .5),
            GOAL = new Sound(3, 1.6), BEEP = new Sound(4, .12), GO = new Sound(5, .3), CLICK = new Sound(6, .04),
            LEVEL_UP = new Sound(7, .7), VICTORY = new Sound(8, 1.2);
    static final Sound[] ALL = {KICK, BOUNCE, WHISTLE, GOAL, BEEP, GO, CLICK, LEVEL_UP, VICTORY};
    static boolean muted;
    final int type; final double seconds; final Clip[] clips = new Clip[3];
    int next; boolean loaded;
    Sound(int type, double seconds) { this.type = type; this.seconds = seconds; }
    // Makes every clip on a background thread at start-up, so the first goal does not wait.
    static class Loader implements Runnable {
        public void run() { for (Sound sound : ALL) sound.load(); }
    }
    static void warmUp() { Server.background(new Loader()); }
    synchronized void load() {
        if (loaded) return;
        loaded = true; // only one try, even when it fails
        try {
            byte[] pcm = synthesize();
            for (int i = 0; i < clips.length; i++) {
                clips[i] = AudioSystem.getClip();
                clips[i].open(new AudioFormat(RATE, 16, 1, true, false), pcm, 0, pcm.length);
            }
        } catch (Throwable unavailable) { muted = true; } // no sound device: keep playing without sound
    }
    synchronized void play() {
        if (muted) return;
        load();
        Clip clip = clips[next++ % clips.length];
        if (clip == null) return;
        clip.stop(); clip.setFramePosition(0); clip.start();
    }
    byte[] synthesize() {
        int samples = (int) (RATE * seconds);
        byte[] pcm = new byte[samples * 2];
        Random noise = new Random(7);
        for (int i = 0; i < samples; i++) {
            double t = i / RATE, p = i / (double) samples, n = noise.nextDouble() * 2 - 1, v = 0;
            int step = (int) (p * 4); // the two tunes have four notes
            switch (type) {
                case 0: v = wave(150 - 90 * p, t) * Math.exp(-p * 7) + n * .35 * Math.exp(-p * 40); break; // kick: thud
                case 1: v = wave(320, t) * Math.exp(-p * 12) * .5 + n * .2 * Math.exp(-p * 25); break; // bounce
                case 2: v = wave(2700 + 80 * wave(28, t), t) * fade(p, .04, .25) * .45; break; // whistle: warbling tone
                case 3: v = (wave(392, t) + wave(494, t) + wave(587, t)) * .18 * fade(p, .01, .3) // goal: horn chord
                        + n * .35 * fade(p, .1, .5) * (1 + .3 * wave(3, t)); break; // and crowd roar
                case 4: v = wave(880, t) * fade(p, .02, .3) * .4; break; // countdown beep
                case 5: v = wave(1320, t) * fade(p, .02, .4) * .45; break; // "go" beep
                case 6: v = wave(1100, t) * Math.exp(-p * 9) * .3; break; // button click
                case 7: v = wave(new double[]{523, 659, 784, 1047}[step], t) * fade(p, .02, .25) * .35; break; // level up
                case 8: v = (wave(new double[]{523, 523, 659, 784}[step], t) // victory fanfare
                        + .5 * wave(new double[]{262, 262, 330, 392}[step], t)) * fade(p, .02, .3) * .3; break;
            }
            short sample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v * 26000));
            pcm[2 * i] = (byte) sample; pcm[2 * i + 1] = (byte) (sample >> 8); // 16-bit little-endian
        }
        return pcm;
    }
    static double wave(double hertz, double t) { return Math.sin(2 * Math.PI * hertz * t); }
    static double fade(double p, double in, double out) { return Math.min(1, Math.min(p / in, (1 - p) / out)); }
}
