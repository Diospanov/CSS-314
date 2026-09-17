import java.util.Random;

public class PhantomBug {

    static final long TOTAL_POINTS = 50_000_000L;
    static final int NUM_THREADS = 4;

    // Intentionally NOT synchronized or atomic.
    static long totalHits = 0;

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
                    totalHits++; // DATA RACE!
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        long pointsPerThread = TOTAL_POINTS / NUM_THREADS;

        totalHits = 0;

        Thread[] threads = new Thread[NUM_THREADS];

        long startTime = System.nanoTime();

        // Create 4 threads
        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new Worker(pointsPerThread);
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        long endTime = System.nanoTime();

        double pi = 4.0 * totalHits / TOTAL_POINTS;
        double timeSeconds = (endTime - startTime) / 1_000_000_000.0;

        System.out.println("Total points: " + TOTAL_POINTS);
        System.out.println("Total hits: " + totalHits);
        System.out.printf("Pi approximation: %.10f%n", pi);
        System.out.printf("Execution time: %.4f seconds%n", timeSeconds);
    }
}