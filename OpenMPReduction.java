import java.util.Random;

public class OpenMPReduction {

    static final long TOTAL_POINTS = 100_000_000L;

    static class Worker extends Thread {
        private final long points;
        private long localHits = 0;

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
                    localHits++;
                }
            }
        }

        public long getLocalHits() {
            return localHits;
        }
    }

    static double runExperiment(int numThreads) throws InterruptedException {

        long pointsPerThread = TOTAL_POINTS / numThreads;

        Worker[] workers = new Worker[numThreads];

        long start = System.nanoTime();

        // Create and start threads
        for (int i = 0; i < numThreads; i++) {
            workers[i] = new Worker(pointsPerThread);
            workers[i].start();
        }

        // Wait for all threads
        for (Worker worker : workers) {
            worker.join();
        }

        // Reduction: combine local counters ONCE
        long totalHits = 0;

        for (Worker worker : workers) {
            totalHits += worker.getLocalHits();
        }

        long end = System.nanoTime();

        double pi = 4.0 * totalHits / TOTAL_POINTS;
        double runtimeMs = (end - start) / 1_000_000.0;

        System.out.printf(
            "%2d threads: Pi = %.8f, Runtime = %.3f ms%n",
            numThreads, pi, runtimeMs
        );

        return runtimeMs;
    }

    public static void main(String[] args) throws InterruptedException {

        int[] threadCounts = {1, 2, 4, 8, 16, 32};

        double baseline = 0;

        System.out.println("===== PART 3: OPENMP-STYLE REDUCTION =====");
        System.out.println("Iterations: " + TOTAL_POINTS);
        System.out.println();

        for (int threads : threadCounts) {

            double runtime = runExperiment(threads);

            if (threads == 1) {
                baseline = runtime;
            }

            double speedup = baseline / runtime;
            double efficiency = speedup / threads * 100.0;

            System.out.printf(
                "Speedup = %.3fx | Efficiency = %.2f%%%n%n",
                speedup,
                efficiency
            );
        }
    }
}