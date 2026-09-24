from concurrent.futures import ThreadPoolExecutor
import threading
import time
import math
import csv


# Workload for each thread
def worker_task(thread_id: int, team_size: int):

    native_tid = threading.get_native_id()

    role = "Master" if thread_id == 0 else "Worker"

    # Computational workload
    total = 0.0

    for i in range(10_000_000):
        total += math.sqrt(i)

    result = (
        f"[{role}] Logical Rank: {thread_id} of {team_size} "
        f"| Native OS TID: {native_tid} "
        f"| Result: {total:.2f}"
    )

    print(result)

    return result


def run_team(num_threads: int):

    print(f"\n--- Forking a team of {num_threads} threads ---")

    start_time = time.perf_counter()

    with ThreadPoolExecutor(max_workers=num_threads) as executor:

        futures = [
            executor.submit(worker_task, tid, num_threads)
            for tid in range(num_threads)
        ]

        # Wait for all threads
        for f in futures:
            f.result()

    end_time = time.perf_counter()

    elapsed = end_time - start_time

    print("--- Joined thread team ---")
    print(
        f"Threads: {num_threads} | "
        f"Time: {elapsed:.6f} seconds"
    )

    return elapsed


if __name__ == "__main__":

    P = [1, 2, 4, 8, 16, 32, 64]

    results = []

    for num_threads in P:

        elapsed = run_team(num_threads)

        results.append(
            (num_threads, elapsed)
        )

    # Save results
    with open(
        "task1_3_results.csv",
        "w",
        newline=""
    ) as file:

        writer = csv.writer(file)

        writer.writerow(
            ["Threads", "Time_seconds"]
        )

        for num_threads, elapsed in results:
            writer.writerow(
                [num_threads, elapsed]
            )

    print("\n=== Task 1.3 Results ===")

    for num_threads, elapsed in results:

        print(
            f"{num_threads:2d} threads "
            f"-> {elapsed:.6f} seconds"
        )