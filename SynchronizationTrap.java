import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class SynchronizationTrap {

    static final long TOTAL_POINTS = 50_000_000L;
    static final int NUM_THREADS = 4;

    // ============================================================
    // 1. SINGLE-THREADED VERSION
    // ============================================================
    static void singleThreaded() {
        long hits = 0;
        Random random = new Random();

        long start = System.nanoTime();

        for (long i = 0; i < TOTAL_POINTS; i++) {
            double x = random.nextDouble();
            double y = random.nextDouble();

            if (x * x + y * y <= 1.0) {
                hits++;
            }
        }

        long end = System.nanoTime();

        double pi = 4.0 * hits / TOTAL_POINTS;
        double time = (end - start) / 1_000_000_000.0;

        System.out.printf(
            "Single-threaded: Pi = %.8f, Time = %.4f s%n",
            pi, time
        );
    }

    // ============================================================
    // 2. FOUR THREADS + ATOMICLONG
    // ============================================================
    static AtomicLong totalHits = new AtomicLong(0);

    static class Worker extends Thread {

        private final long points;

        Worker(long points) {
            this.points = points;
        }

        @Override
        public void run() {
            Random random = new Random();

            for (long i = 0; i < points; i++) {
                double x = random.nextDouble();
                double y = random.nextDouble();

                if (x * x + y * y <= 1.0) {
                    totalHits.incrementAndGet();
                }
            }
        }
    }

    static void fourThreadsAtomic() throws InterruptedException {

        totalHits.set(0);

        long pointsPerThread = TOTAL_POINTS / NUM_THREADS;

        Thread[] threads = new Thread[NUM_THREADS];

        long start = System.nanoTime();

        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new Worker(pointsPerThread);
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        long end = System.nanoTime();

        double pi = 4.0 * totalHits.get() / TOTAL_POINTS;
        double time = (end - start) / 1_000_000_000.0;

        System.out.printf(
            "4 threads AtomicLong: Pi = %.8f, Time = %.4f s%n",
            pi, time
        );
    }

    // ============================================================
    // MAIN
    // ============================================================
    public static void main(String[] args) throws InterruptedException {

        System.out.println("===== PART 2 =====");

        singleThreaded();

        fourThreadsAtomic();
    }
}