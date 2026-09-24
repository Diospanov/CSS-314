import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CollatzLab {
    static final long MOD = 1_000_000_007L;
    static final long DEFAULT_ID_LAST4 = 3163L;

    enum ScheduleKind {
        STATIC_DEFAULT,
        STATIC_CHUNK,
        DYNAMIC,
        GUIDED
    }

    static final class KernelResult {
        long maxSteps;
        long checksum;
        long hits;
    }

    static final class TimedResult {
        final double seconds;
        final KernelResult result;

        TimedResult(double seconds, KernelResult result) {
            this.seconds = seconds;
            this.result = result;
        }
    }

    static final class WorkerResult {
        long maxSteps;
        long checksum;
        long hits;
    }

    static final class PaddedCounter {
        volatile long value;
        long p1, p2, p3, p4, p5, p6, p7;
    }

    static int collatzSteps(long n) {
        int steps = 0;
        while (n > 1) {
            if ((n & 1L) == 0L) {
                n >>= 1;
            } else {
                n = 3L * n + 1L;
            }
            steps++;
        }
        return steps;
    }

    static long workloadFromLast4(long last4) {
        return 10_000_000L + last4 * 1_000L;
    }

    static TimedResult sequential(long n) {
        KernelResult result = new KernelResult();
        long checksumSum = 0L;
        long start = System.nanoTime();

        for (long i = 1L; i <= n; i++) {
            int steps = collatzSteps(i);
            if (steps > result.maxSteps) {
                result.maxSteps = steps;
            }
            checksumSum += steps;
            if (steps > 100) {
                result.hits++;
            }
        }

        result.checksum = checksumSum % MOD;
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        return new TimedResult(seconds, result);
    }

    static TimedResult parallelStatic(long n, int threads) throws InterruptedException {
        WorkerResult[] results = new WorkerResult[threads];
        Thread[] workers = new Thread[threads];
        long chunk = n / threads;

        long startTime = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            final long start = tid * chunk + 1L;
            final long end = tid == threads - 1 ? n : start + chunk - 1L;
            results[tid] = new WorkerResult();
            workers[tid] = new Thread(() -> runRange(start, end, results[tid]));
            workers[tid].start();
        }

        joinAll(workers);
        double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
        return new TimedResult(seconds, combine(results));
    }

    static void runRange(long start, long end, WorkerResult result) {
        long checksumSum = 0L;
        for (long i = start; i <= end; i++) {
            int steps = collatzSteps(i);
            if (steps > result.maxSteps) {
                result.maxSteps = steps;
            }
            checksumSum += steps;
            if (steps > 100) {
                result.hits++;
            }
        }
        result.checksum = checksumSum % MOD;
    }

    static TimedResult falseSharingNaive(long n, int threads) throws InterruptedException {
        WorkerResult[] results = new WorkerResult[threads];
        Thread[] workers = new Thread[threads];
        long[] sharedHits = new long[threads];
        long chunk = n / threads;

        long startTime = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            final long start = tid * chunk + 1L;
            final long end = tid == threads - 1 ? n : start + chunk - 1L;
            results[tid] = new WorkerResult();
            workers[tid] = new Thread(() -> {
                long checksumSum = 0L;
                for (long i = start; i <= end; i++) {
                    int steps = collatzSteps(i);
                    if (steps > results[tid].maxSteps) {
                        results[tid].maxSteps = steps;
                    }
                    checksumSum += steps;
                    if (steps > 100) {
                        sharedHits[tid]++;
                    }
                }
                results[tid].checksum = checksumSum % MOD;
            });
            workers[tid].start();
        }

        joinAll(workers);
        double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
        KernelResult combined = combine(results);
        combined.hits = 0L;
        for (long hit : sharedHits) {
            combined.hits += hit;
        }
        return new TimedResult(seconds, combined);
    }

    static TimedResult falseSharingPadded(long n, int threads) throws InterruptedException {
        WorkerResult[] results = new WorkerResult[threads];
        Thread[] workers = new Thread[threads];
        PaddedCounter[] sharedHits = new PaddedCounter[threads];
        long chunk = n / threads;

        for (int i = 0; i < threads; i++) {
            sharedHits[i] = new PaddedCounter();
        }

        long startTime = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            final long start = tid * chunk + 1L;
            final long end = tid == threads - 1 ? n : start + chunk - 1L;
            results[tid] = new WorkerResult();
            workers[tid] = new Thread(() -> {
                long checksumSum = 0L;
                for (long i = start; i <= end; i++) {
                    int steps = collatzSteps(i);
                    if (steps > results[tid].maxSteps) {
                        results[tid].maxSteps = steps;
                    }
                    checksumSum += steps;
                    if (steps > 100) {
                        sharedHits[tid].value++;
                    }
                }
                results[tid].checksum = checksumSum % MOD;
            });
            workers[tid].start();
        }

        joinAll(workers);
        double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
        KernelResult combined = combine(results);
        combined.hits = 0L;
        for (PaddedCounter hit : sharedHits) {
            combined.hits += hit.value;
        }
        return new TimedResult(seconds, combined);
    }

    static TimedResult scheduled(long n, int threads, ScheduleKind kind, long chunkSize) throws InterruptedException {
        WorkerResult[] results = new WorkerResult[threads];
        Thread[] workers = new Thread[threads];
        AtomicLong next = new AtomicLong(1L);

        long startTime = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            results[tid] = new WorkerResult();
            workers[tid] = new Thread(() -> {
                if (kind == ScheduleKind.STATIC_DEFAULT) {
                    long chunk = n / threads;
                    long start = tid * chunk + 1L;
                    long end = tid == threads - 1 ? n : start + chunk - 1L;
                    runRange(start, end, results[tid]);
                } else if (kind == ScheduleKind.STATIC_CHUNK) {
                    runStaticChunks(n, threads, tid, chunkSize, results[tid]);
                } else {
                    runDynamicChunks(n, threads, kind, chunkSize, next, results[tid]);
                }
            });
            workers[tid].start();
        }

        joinAll(workers);
        double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
        return new TimedResult(seconds, combine(results));
    }

    static void runStaticChunks(long n, int threads, int tid, long chunkSize, WorkerResult result) {
        long checksumSum = 0L;
        long stride = chunkSize * threads;
        for (long start = 1L + tid * chunkSize; start <= n; start += stride) {
            long end = Math.min(n, start + chunkSize - 1L);
            checksumSum = runChunk(start, end, result, checksumSum);
        }
        result.checksum = checksumSum % MOD;
    }

    static void runDynamicChunks(
            long n,
            int threads,
            ScheduleKind kind,
            long chunkSize,
            AtomicLong next,
            WorkerResult result
    ) {
        long checksumSum = 0L;
        while (true) {
            long start;
            long size;
            if (kind == ScheduleKind.GUIDED) {
                while (true) {
                    start = next.get();
                    if (start > n) {
                        result.checksum = checksumSum % MOD;
                        return;
                    }
                    long remaining = n - start + 1L;
                    size = Math.max(1_000L, remaining / (threads * 2L));
                    long candidate = start + size;
                    if (next.compareAndSet(start, candidate)) {
                        break;
                    }
                }
            } else {
                size = chunkSize;
                start = next.getAndAdd(size);
                if (start > n) {
                    result.checksum = checksumSum % MOD;
                    return;
                }
            }
            long end = Math.min(n, start + size - 1L);
            checksumSum = runChunk(start, end, result, checksumSum);
        }
    }

    static long runChunk(long start, long end, WorkerResult result, long checksumSum) {
        for (long i = start; i <= end; i++) {
            int steps = collatzSteps(i);
            if (steps > result.maxSteps) {
                result.maxSteps = steps;
            }
            checksumSum += steps;
            if (steps > 100) {
                result.hits++;
            }
        }
        return checksumSum;
    }

    static KernelResult combine(WorkerResult[] workerResults) {
        KernelResult combined = new KernelResult();
        long checksumSum = 0L;
        for (WorkerResult result : workerResults) {
            if (result.maxSteps > combined.maxSteps) {
                combined.maxSteps = result.maxSteps;
            }
            checksumSum += result.checksum;
            combined.hits += result.hits;
        }
        combined.checksum = checksumSum % MOD;
        return combined;
    }

    static void joinAll(Thread[] workers) throws InterruptedException {
        for (Thread worker : workers) {
            worker.join();
        }
    }

    static int[] threadCases(int maxThreads) {
        int[] candidates = {1, 2, 4, 8, 16, 32, 64};
        List<Integer> cases = new ArrayList<>();
        for (int candidate : candidates) {
            if (candidate <= maxThreads) {
                cases.add(candidate);
            }
        }
        if (cases.isEmpty() || cases.get(cases.size() - 1) != maxThreads) {
            cases.add(maxThreads);
        }
        return cases.stream().mapToInt(Integer::intValue).toArray();
    }

    static double amdahlSpeedup(double p, int k) {
        return 1.0 / ((1.0 - p) + (p / k));
    }

    static void writeCsvRow(
            PrintWriter csv,
            String section,
            String variant,
            int threads,
            String run1,
            String run2,
            String run3,
            double avg,
            String sEmp,
            String sTheo,
            String delta,
            KernelResult result,
            String penaltyRatio,
            String notes
    ) {
        double throughput = avg > 0.0 ? currentN / avg : 0.0;
        csv.printf(
                Locale.US,
                "%s,%s,%d,%s,%s,%s,%.9f,%s,%s,%s,%d,%d,%d,%.3f,%s,%s%n",
                section,
                variant,
                threads,
                run1,
                run2,
                run3,
                avg,
                sEmp,
                sTheo,
                delta,
                result.maxSteps,
                result.checksum,
                result.hits,
                throughput,
                penaltyRatio,
                notes
        );
    }

    static long currentN;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        long n = workloadFromLast4(DEFAULT_ID_LAST4);
        int maxThreads = Runtime.getRuntime().availableProcessors();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--id-last4" -> n = workloadFromLast4(Long.parseLong(args[++i]));
                case "--n" -> n = Long.parseLong(args[++i]);
                case "--max-threads" -> maxThreads = Integer.parseInt(args[++i]);
                default -> {
                    System.err.println("Usage: java CollatzLab [--id-last4 NNNN] [--n WORKLOAD] [--max-threads T]");
                    return;
                }
            }
        }

        currentN = n;
        int[] threadCounts = threadCases(maxThreads);
        Path csvPath = Path.of("results.csv");

        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
            csv.println("section,variant,threads,run1_cold_s,run2_s,run3_s,avg_s,s_emp,s_theo,delta,max_steps,checksum,hits,throughput_iter_s,penalty_ratio,notes");

            System.out.println("Amdahl Reality Gap - Java Collatz Lab");
            System.out.println("N = " + n);
            System.out.println("Available processors = " + Runtime.getRuntime().availableProcessors());
            System.out.println("Benchmark max threads = " + maxThreads);
            System.out.println();

            TimedResult seq1 = sequential(n);
            TimedResult seq2 = sequential(n);
            TimedResult seq3 = sequential(n);
            double tSeq = (seq2.seconds + seq3.seconds) / 2.0;

            System.out.printf("Sequential cold %.6f s, run2 %.6f s, run3 %.6f s, T_seq %.6f s%n",
                    seq1.seconds, seq2.seconds, seq3.seconds, tSeq);
            System.out.printf("Max steps %d, checksum %d, hits>100 %d%n%n",
                    seq3.result.maxSteps, seq3.result.checksum, seq3.result.hits);

            writeCsvRow(csv, "phase2", "sequential", 1,
                    fmt(seq1.seconds), fmt(seq2.seconds), fmt(seq3.seconds), tSeq,
                    "1.000000000", "1.000000000", "0.000000000",
                    seq3.result, "", "warmup discarded");

            double[] run1Times = new double[threadCounts.length];
            double[] run2Times = new double[threadCounts.length];
            double[] run3Times = new double[threadCounts.length];
            double[] avgTimes = new double[threadCounts.length];
            KernelResult[] scaleResults = new KernelResult[threadCounts.length];
            double p = 1.0;

            for (int i = 0; i < threadCounts.length; i++) {
                int threads = threadCounts[i];
                TimedResult r1 = parallelStatic(n, threads);
                TimedResult r2 = parallelStatic(n, threads);
                TimedResult r3 = parallelStatic(n, threads);
                double avg = (r2.seconds + r3.seconds) / 2.0;
                double sEmp = tSeq / avg;

                run1Times[i] = r1.seconds;
                run2Times[i] = r2.seconds;
                run3Times[i] = r3.seconds;
                avgTimes[i] = avg;
                scaleResults[i] = r3.result;

                if (threads == 2) {
                    p = 2.0 * (1.0 - (1.0 / sEmp));
                    p = Math.max(0.0, Math.min(1.0, p));
                }

                System.out.printf("%2d thread(s): cold %.6f, run2 %.6f, run3 %.6f, avg %.6f, S_emp %.4f%n",
                        threads, r1.seconds, r2.seconds, r3.seconds, avg, sEmp);
            }

            System.out.printf("%nDerived p from k=2: %.6f%n%n", p);

            for (int i = 0; i < threadCounts.length; i++) {
                int threads = threadCounts[i];
                double sEmp = tSeq / avgTimes[i];
                double sTheo = amdahlSpeedup(p, threads);
                double delta = sTheo - sEmp;
                writeCsvRow(csv, "phase3", "java_static", threads,
                        fmt(run1Times[i]), fmt(run2Times[i]), fmt(run3Times[i]), avgTimes[i],
                        fmt(sEmp), fmt(sTheo), fmt(delta), scaleResults[i], "", "Java fixed thread ranges");
            }

            int experimentThreads = maxThreads;
            TimedResult naive = falseSharingNaive(n, experimentThreads);
            TimedResult padded = falseSharingPadded(n, experimentThreads);
            double penaltyRatio = naive.seconds / padded.seconds;

            System.out.printf("False sharing naive: %.6f s, padded: %.6f s, penalty ratio %.4f%n%n",
                    naive.seconds, padded.seconds, penaltyRatio);

            writeCsvRow(csv, "phase4a", "java_naive_long_array", experimentThreads,
                    "", "", "", naive.seconds, "", "", "", naive.result, fmt(penaltyRatio), "shared long[] hits[tid]++");
            writeCsvRow(csv, "phase4a", "java_padded_counter", experimentThreads,
                    "", "", "", padded.seconds, "", "", "", padded.result, "1.000000000", "padded per-thread counter");

            runScheduleCase(csv, n, experimentThreads, "static_default", ScheduleKind.STATIC_DEFAULT, 0L, "contiguous fixed ranges");
            runScheduleCase(csv, n, experimentThreads, "static_1000", ScheduleKind.STATIC_CHUNK, 1_000L, "round-robin 1000-item chunks");
            runScheduleCase(csv, n, experimentThreads, "dynamic_100", ScheduleKind.DYNAMIC, 100L, "AtomicLong work queue, chunk 100");
            runScheduleCase(csv, n, experimentThreads, "dynamic_10000", ScheduleKind.DYNAMIC, 10_000L, "AtomicLong work queue, chunk 10000");
            runScheduleCase(csv, n, experimentThreads, "guided", ScheduleKind.GUIDED, 0L, "decreasing chunk size");
        }

        System.out.println();
        System.out.println("Wrote results.csv");
    }

    static void runScheduleCase(
            PrintWriter csv,
            long n,
            int threads,
            String variant,
            ScheduleKind kind,
            long chunk,
            String notes
    ) throws InterruptedException {
        TimedResult result = scheduled(n, threads, kind, chunk);
        System.out.printf("%-15s %9.6f s%n", variant, result.seconds);
        writeCsvRow(csv, "phase4b", variant, threads,
                "", "", "", result.seconds, "", "", "", result.result, "", notes);
    }

    static String fmt(double value) {
        return String.format(Locale.US, "%.9f", value);
    }
}
