package demo;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/**
 * A game that runs its loop on its own thread, the way most of them did.
 *
 * <p>The shape is the one every J2ME game book taught: the Canvas starts a
 * Thread, the thread loops until told to stop, and the two sides talk through
 * a lock — {@code wait} on one side, {@code notify} on the other. Everything
 * this fixture asks of the emulator is something that loop needs to be true.</p>
 */
public final class ThreadDemo extends MIDlet {

    /** Turns the game thread completed. */
    public int turns;
    /** True when the thread saw itself as the thread it was started as. */
    public boolean sawItself;
    /** The name the game gave its thread, read back from inside it. */
    public String nameInside = "";
    /** Threads alive while the game loop was running. */
    public int aliveDuring;
    /** Counter touched from both sides, guarded by a synchronized method. */
    private int shared;
    /** True once the waiter was woken by notify rather than by a timeout. */
    public boolean woken;

    private final Object lock = new Object();
    private Thread loop;
    private volatile boolean playing;

    protected void startApp() {
        Display.getDisplay(this).setCurrent(new Scene());
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
        playing = false;
    }

    /** Starts the game loop on its own thread and waits for it to finish. */
    public void play() throws InterruptedException {
        playing = true;
        Runnable body = new Runnable() {
            public void run() {
                sawItself = Thread.currentThread() == loop;
                nameInside = Thread.currentThread().getName();
                aliveDuring = Thread.activeCount();
                for (int i = 0; i < 8 && playing; i++) {
                    bump();
                    turns++;
                }
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };
        loop = new Thread(body, "vòng lặp game");
        synchronized (lock) {
            loop.start();
            // Đợi vòng lặp báo xong. Hết giờ mà không ai gọi thì woken vẫn sai.
            lock.wait(4000);
            woken = turns > 0;
        }
        loop.join();
    }

    /** Both sides go through here, so the count may not be lost. */
    public synchronized void bump() {
        shared++;
    }

    public int shared() {
        return shared;
    }

    /** How many times the waiter came out of {@code wait}. */
    public int wakeups;
    /** How many times the other side took and released the same lock. */
    public int knocks;
    private boolean ready;

    /**
     * Waits to be told, while another thread keeps taking the same lock.
     *
     * <p>This is the shape of every paced game loop: one side waits on a lock,
     * the other side touches that lock for its own reasons and only signals
     * when there is really something to say. Taking and releasing a lock is
     * not a signal, so the waiter must come out exactly once — when it is
     * told, not when the lock is merely busy.</p>
     */
    public void waitToBeTold() throws InterruptedException {
        Thread other = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 200; i++) {
                    synchronized (lock) {
                        knocks++;
                    }
                }
                synchronized (lock) {
                    ready = true;
                    lock.notifyAll();
                }
            }
        }, "bên kia");
        synchronized (lock) {
            other.start();
            while (!ready) {
                lock.wait(4000);
                wakeups++;
            }
        }
        other.join();
    }

    /** How many items made it from one side to the other. */
    public int handedOver;

    private final java.util.Vector queue = new java.util.Vector();
    private static final int BATCH = 200;

    /**
     * A producer and a consumer passing items through one lock.
     *
     * <p>The oldest shape in the book, and the one a J2ME game uses to move
     * frames from its loop thread to the painter: one side waits on a lock,
     * the other adds and notifies. Both sides go through {@code wait} and
     * {@code notify} on the <em>same</em> object hundreds of times in a row,
     * which is exactly the traffic that finds a lock taken in the wrong
     * order.</p>
     */
    public void handOff() throws InterruptedException {
        handedOver = 0;
        queue.removeAllElements();
        Thread consumer = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < BATCH; i++) {
                    synchronized (lock) {
                        while (queue.isEmpty()) {
                            try {
                                lock.wait(2000);
                            } catch (InterruptedException stop) {
                                return;
                            }
                        }
                        queue.removeElementAt(0);
                        handedOver++;
                        lock.notifyAll();
                    }
                }
            }
        }, "bên nhận");
        consumer.start();
        for (int i = 0; i < BATCH; i++) {
            synchronized (lock) {
                queue.addElement("món " + i);
                lock.notifyAll();
                while (queue.size() > 2) {
                    lock.wait(2000);
                }
            }
        }
        consumer.join();
    }

    /** Starts a loop thread that never comes back, the classic way to freeze. */
    public void hangOnThread() {
        Thread stuck = new Thread(new Runnable() {
            public void run() {
                int spin = 0;
                while (spin >= 0) {
                    spin++;
                    if (spin == Integer.MAX_VALUE) {
                        spin = 0;
                    }
                }
            }
        }, "vòng lặp game");
        stuck.start();
    }

    /** Starts a loop thread that dies on the first turn. */
    public void breakOnThread() {
        Thread doomed = new Thread(new Runnable() {
            public void run() {
                int[] tiles = new int[4];
                tiles[9] = 1;
            }
        }, "vẽ nền");
        doomed.start();
    }

    /** The name of the thread the emulator itself runs the MIDlet on. */
    public String mainName() {
        return Thread.currentThread().getName();
    }

    private static final class Scene extends Canvas {
        protected void paint(Graphics g) {
            g.setColor(0x101820);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
