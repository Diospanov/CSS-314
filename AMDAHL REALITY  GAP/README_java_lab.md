# Java Version - Amdahl Reality Gap Lab

Your worksheet is written for C/OpenMP. This Java version mirrors the same experiments with Java threads:

- sequential Collatz baseline with warmup discarded
- fixed-range multi-thread scaling
- empirical Amdahl `p` from `k = 2`
- false-sharing style counter test
- static, dynamic, and guided scheduling simulations
- `results.csv` output for tables and plotting

If your instructor strictly requires OpenMP source, submit the C version. If Java is accepted, use this file.

## Compile

```bash
javac CollatzLab.java
```

## Run

Default workload uses last four ID digits `3163`:

```bash
java CollatzLab
```

With your real last four ID digits:

```bash
java CollatzLab --id-last4 3163
```

Limit to 16 threads:

```bash
java CollatzLab --id-last4 3163 --max-threads 16
```

The program writes `results.csv`.

## Plot

```bash
python make_plot.py
```

The plot script reads `phase3` rows from `results.csv` and writes `speedup_plot.png`.

## Important wording for your report

Because this is Java, write "Java threads" instead of "OpenMP threads" in your explanation unless your instructor told you Java is acceptable. The hardware ideas are still the same: real cores, cache lines, coherence traffic, scheduling overhead, and SMT resource sharing.
