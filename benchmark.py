import math
import time
import multiprocessing as mp


# Compute-bound workload
def compute(start_end):
    start, end = start_end
    result = 0.0

    for i in range(start, end):
        x = float(i)

        result += (
            math.sin(x)
            * math.cos(x)
            * math.sqrt(x)
            * math.sqrt(x)
        )

        result += math.log(x + 1.0) * math.exp(-x / 100_000_000)

    return result


def run_benchmark(num_processes, n=100_000_000):
    # Divide work between processes
    chunk_size = n // num_processes

    ranges = []

    for i in range(num_processes):
        start = i * chunk_size

        if i == num_processes - 1:
            end = n
        else:
            end = (i + 1) * chunk_size

        ranges.append((start, end))

    start_time = time.perf_counter()

    with mp.Pool(processes=num_processes) as pool:
        results = pool.map(compute, ranges)

    end_time = time.perf_counter()

    # Prevent compiler/interpreter from treating result as irrelevant
    total = sum(results)

    return end_time - start_time, total


if __name__ == "__main__":

    thread_counts = [1, 2, 4, 8, 16, 32]

    print("Compute-bound CPU Benchmark")
    print("Language: Python")
    print("Runtime: multiprocessing")
    print("Workload: Numerical computation")
    print("Iterations: 100,000,000")
    print()

    for workers in thread_counts:

        print(f"Running with {workers} processes...")

        times = []

        for run in range(3):

            elapsed, result = run_benchmark(workers)

            times.append(elapsed)

            print(f"  Run {run + 1}: {elapsed:.3f} s")

        average = sum(times) / len(times)

        print(f"  Average: {average:.3f} s")
        print()