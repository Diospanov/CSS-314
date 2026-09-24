# Amdahl Reality Gap - Written Defense

Student name: Dias
Student ID: 230103163
CPU: 12th Gen Intel(R) Core(TM) i5-12500H
Physical cores: 12
Logical threads: 16
Workload N: 13,163,000 if last four ID digits are 3163
Cache line size: usually 64 bytes on this CPU family; verify if your instructor requires proof.
Implementation language: Java threads

## Values to copy from results.csv

- T_seq: 3.059967300 s
- T_2: 1.723972300 s
- S_emp(2): 1.774951546
- Derived p = 2 * (1 - 1 / S_emp(2)): 0.873209
- Best empirical speedup: 8.628397997 at 16 threads
- False sharing naive time: 0.396742800 s
- Padded/reduction time: 0.552258000 s
- Penalty ratio: 0.718401182
- Best scheduling clause: dynamic_10000 at 0.513364000 s
- Worst scheduling clause: static_1000 at 0.637775600 s

## Table 1: Empirical Measurement Log

| Threads (k) | Run 1 Cold (s) | Run 2 (s) | Run 3 (s) | Avg T_k (s) | S_emp(k) | S_theo(k) | Delta(k) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 3.285280800 | 3.305904500 | 3.085860700 | 3.195882600 | 0.957471748 | 1.000000000 | 0.042528252 |
| 2 | 1.752242200 | 1.714909200 | 1.733035400 | 1.723972300 | 1.774951546 | 1.774951546 | 0.000000000 |
| 4 | 0.918732200 | 0.868976100 | 0.805103900 | 0.837040000 | 3.655700205 | 2.897765458 | -0.757934747 |
| 8 | 0.470885000 | 0.468337200 | 0.464033800 | 0.466185500 | 6.563840574 | 4.238322449 | -2.325518126 |
| 16 | 0.329185400 | 0.359726700 | 0.349551500 | 0.354639100 | 8.628397997 | 5.513686318 | -3.114711678 |

Sequential baseline used for speedup: T_seq = 3.059967300 s.

## Table 2: False Sharing Experiment

| Implementation Variant | Thread Count | Execution Time (s) | Effective Throughput (iter/sec) | Speedup Penalty Ratio |
|---|---:|---:|---:|---:|
| Variant 1: Naive `hits[tid]++` / Java `long[]` | 16 | 0.396742800 | 33,177,665.732 | 0.718401182 |
| Variant 2: Padded per-thread counter | 16 | 0.552258000 | 23,834,874.280 | 1.000000000 |

Note: this Java run did not show the expected native C/OpenMP false-sharing slowdown; the naive Java array version measured faster than the padded object version.

## Table 3: Loop Scheduling Experiment

| Scheduling Clause | Chunk Size | Execution Time (s) | Observed Behavior & CPU Load Distribution |
|---|---:|---:|---|
| Java static default | Default N / k | 0.541831400 | Contiguous fixed ranges; low scheduling overhead but possible range imbalance. |
| Java static chunk | 1,000 | 0.637775600 | Round-robin chunks; slower in this run, likely due to extra chunking/locality cost. |
| Java dynamic | 100 | 0.515695700 | Fine-grained work queue; good balance with slightly more queue overhead. |
| Java dynamic | 10,000 | 0.513364000 | Best result; balanced work while reducing queue overhead. |
| Java guided | Decreasing chunks | 0.540193300 | Adaptive chunk sizes; close to static default in this run. |

## Q1: Micro-Architectural Root Cause of False Sharing

In a native C/OpenMP implementation, Variant 1 is expected to be slow because each thread updates a different array element, but adjacent `int` or `long` elements can sit in the same 64-byte cache line. Even though the threads are logically updating separate counters, the cache-coherence protocol treats the whole cache line as the ownership unit. Under MESI/MOESI, every write can force other cores' copies of that line to be invalidated or transferred, so the line bounces between cores and creates invalidation traffic.

In my Java run, the naive `long[]` counter version measured 0.396742800 s, while the padded counter version measured 0.552258000 s, so the expected slowdown did not appear. A likely explanation is that Java/JIT behavior, object layout, optimization of non-volatile array updates, and the extra overhead of padded objects changed the measurement compared with a native OpenMP `int hit_count[MAX_THREADS]` test. The hardware principle is still cache-line invalidation, but this Java result should be reported as an empirical exception rather than forced to match the expected C/OpenMP behavior.

## Q2: Hyperthreading / SMT Saturation

My speedup at 16 logical threads was 8.628397997. The CPU has 12 physical cores and 16 logical threads, so the final step includes SMT/hybrid-core effects rather than only adding full independent cores.

It did not scale linearly to 16x. SMT threads share core resources such as execution ports, front-end decode bandwidth, branch prediction structures, private caches, load/store queues, and memory bandwidth. Collatz is also branch-heavy and dependency-heavy integer work, so extra logical threads can help hide stalls but cannot double the real core resources.

## Q3: Empirical vs. Theoretical Amdahl Discrepancy

The derived parallel fraction was p = 0.873209.

The theoretical curve diverged from empirical speedup at higher thread counts because Amdahl's equation does not include real overheads such as thread scheduling, thread startup, reductions, cache-coherence traffic, load imbalance, branch misprediction, memory bandwidth limits, and SMT resource contention. In this Java run, the 2-thread result was relatively conservative, so the Amdahl curve derived from k = 2 predicted lower speedups than the measured 4, 8, and 16-thread results. That shows how sensitive empirical Amdahl fitting is to the measurement used for p.

## Q4: Scheduling Trade-Off Dilemma

On my machine, the best scheduling result was dynamic_10000 at 0.513364000 seconds. The worst result was static_1000 at 0.637775600 seconds.

`dynamic(100)` gives fine-grained balancing, which helps because Collatz iterations have uneven stopping times, but it can create more queue operations. `dynamic(10000)` reduced scheduling overhead while still balancing enough work, which is why it was slightly faster than `dynamic(100)` in this run. Static scheduling has less queue overhead, but the `static_1000` round-robin style was slower here, likely because it increased chunk-management and locality costs without enough balancing benefit. In this Java run, queue contention did not outweigh balancing at chunk 100; `dynamic_100` was close to `dynamic_10000`.
