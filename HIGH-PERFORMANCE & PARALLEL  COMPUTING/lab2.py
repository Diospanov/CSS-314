import numpy as np
import time
import matplotlib.pyplot as plt

from numba import (
    njit,
    prange,
    set_num_threads,
    get_num_threads,
    get_thread_id,
)


# ============================================================
# CONFIGURATION
# ============================================================

TRUE_PI = np.pi

# Task 2.1
RACE_STEPS = 1_000_000

# Task 2.2
CRITICAL_STEPS = 1_000_000

# Task 2.3 / 2.4
REDUCTION_STEPS = 100_000_000

THREAD_COUNTS = [1, 2, 4, 8, 16]

TRIALS = 5


# ============================================================
# VARIANT A
# NAIVE SHARED ACCUMULATOR
# ============================================================

@njit(parallel=True)
def calc_pi_naive(num_steps):

    step = 1.0 / num_steps

    # Shared accumulator
    total_sum = np.zeros(1, dtype=np.float64)

    for i in prange(num_steps):

        x = (i + 0.5) * step

        value = 4.0 / (1.0 + x * x)

        # ----------------------------------------------------
        # RACE CONDITION
        #
        # Multiple threads update the same memory location.
        # There is no synchronization.
        # ----------------------------------------------------

        total_sum[0] += value

    return total_sum[0] * step


# ============================================================
# VARIANT B
# CRITICAL SECTION
# ============================================================

# Numba-compatible lock implementation
from numba import types
from numba.typed import Dict


@njit
def atomic_add(lock, total, value):
    """
    Add value to total while holding the lock.
    """

    while not lock[0]:
        lock[0] = True

        total[0] += value

        lock[0] = False

        return


@njit(parallel=True)
def calc_pi_critical(num_steps):

    step = 1.0 / num_steps

    total_sum = np.zeros(1, dtype=np.float64)

    lock = np.zeros(1, dtype=np.bool_)

    for i in prange(num_steps):

        x = (i + 0.5) * step

        value = 4.0 / (1.0 + x * x)

        # ----------------------------------------------------
        # CRITICAL SECTION
        #
        # Only one thread should update total_sum at a time.
        # ----------------------------------------------------

        atomic_add(lock, total_sum, value)

    return total_sum[0] * step


# ============================================================
# VARIANT C
# PARALLEL REDUCTION
# ============================================================

@njit(parallel=True)
def calc_pi_reduction(num_steps):

    step = 1.0 / num_steps

    total_sum = 0.0

    for i in prange(num_steps):

        x = (i + 0.5) * step

        # Numba recognizes this as a reduction.
        total_sum += 4.0 / (1.0 + x * x)

    return total_sum * step


# ============================================================
# SERIAL BASELINE
# ============================================================

@njit
def calc_pi_serial(num_steps):

    step = 1.0 / num_steps

    total_sum = 0.0

    for i in range(num_steps):

        x = (i + 0.5) * step

        total_sum += 4.0 / (1.0 + x * x)

    return total_sum * step


# ============================================================
# TASK 2.1
# NAIVE RACE CONDITION
# ============================================================

def task_2_1():

    print()
    print("=" * 70)
    print("TASK 2.1 - NAIVE RACE CONDITION")
    print("=" * 70)

    print(f"Number of steps: {RACE_STEPS}")
    print()

    results = []

    for p in [1, 2, 4, 8]:

        set_num_threads(p)

        pi_value = calc_pi_naive(RACE_STEPS)

        error = abs(pi_value - TRUE_PI)

        results.append(
            (p, pi_value, error)
        )

        print(
            f"P = {p:2d} | "
            f"Pi = {pi_value:.12f} | "
            f"Error = {error:.6e}"
        )

    print()

    print("Interpretation:")
    print(
        "The naive version updates a shared accumulator "
        "without synchronization."
    )

    print(
        "Multiple threads can read and write the same value "
        "at the same time."
    )

    print(
        "Therefore, some updates can be lost and the calculated "
        "Pi can deviate from the expected value."
    )

    return results


# ============================================================
# TASK 2.2
# CRITICAL SECTION
# ============================================================

def task_2_2():

    print()
    print("=" * 70)
    print("TASK 2.2 - CRITICAL SECTION OVERHEAD")
    print("=" * 70)

    print(f"Number of steps: {CRITICAL_STEPS}")
    print()

    # --------------------------------------------------------
    # Serial baseline
    # --------------------------------------------------------

    set_num_threads(1)

    start = time.perf_counter()

    pi_serial = calc_pi_serial(CRITICAL_STEPS)

    end = time.perf_counter()

    serial_time = end - start

    serial_error = abs(pi_serial - TRUE_PI)

    print(
        f"Serial    | "
        f"Pi = {pi_serial:.12f} | "
        f"Time = {serial_time:.6f}s | "
        f"Error = {serial_error:.6e}"
    )

    print()

    # --------------------------------------------------------
    # Critical section
    # --------------------------------------------------------

    critical_results = []

    for p in [1, 2, 4, 8]:

        set_num_threads(p)

        start = time.perf_counter()

        pi_critical = calc_pi_critical(CRITICAL_STEPS)

        end = time.perf_counter()

        critical_time = end - start

        error = abs(pi_critical - TRUE_PI)

        overhead = (
            (critical_time - serial_time)
            / serial_time
            * 100
        )

        critical_results.append(
            (
                p,
                pi_critical,
                critical_time,
                error,
                overhead
            )
        )

        print(
            f"P = {p:2d} | "
            f"Pi = {pi_critical:.12f} | "
            f"Time = {critical_time:.6f}s | "
            f"Error = {error:.6e} | "
            f"Overhead = {overhead:.2f}%"
        )

    return serial_time, critical_results


# ============================================================
# TASK 2.3
# PARALLEL REDUCTION
# ============================================================

def task_2_3():

    print()
    print("=" * 70)
    print("TASK 2.3 - PARALLEL REDUCTION")
    print("=" * 70)

    print(f"Number of steps: {REDUCTION_STEPS}")
    print(f"Trials per thread count: {TRIALS}")
    print()

    results = {}

    for p in THREAD_COUNTS:

        set_num_threads(p)

        trial_times = []

        pi_value = None

        for trial in range(TRIALS):

            start = time.perf_counter()

            pi_value = calc_pi_reduction(
                REDUCTION_STEPS
            )

            end = time.perf_counter()

            elapsed = end - start

            trial_times.append(elapsed)

            print(
                f"P = {p:2d} | "
                f"Trial = {trial + 1} | "
                f"Time = {elapsed:.6f}s"
            )

        average_time = np.mean(trial_times)

        std_time = np.std(trial_times)

        error = abs(pi_value - TRUE_PI)

        results[p] = {
            "times": trial_times,
            "average": average_time,
            "std": std_time,
            "pi": pi_value,
            "error": error,
        }

        print(
            f"P = {p:2d} | "
            f"Average = {average_time:.6f}s | "
            f"Std = {std_time:.6f}s | "
            f"Pi = {pi_value:.12f}"
        )

        print("-" * 70)

    return results


# ============================================================
# TASK 2.4
# SPEEDUP AND EFFICIENCY
# ============================================================

def task_2_4(results):

    print()
    print("=" * 70)
    print("TASK 2.4 - SPEEDUP AND EFFICIENCY")
    print("=" * 70)

    thread_counts = list(results.keys())

    times = [
        results[p]["average"]
        for p in thread_counts
    ]

    # --------------------------------------------------------
    # T(1)
    # --------------------------------------------------------

    t1 = times[0]

    # --------------------------------------------------------
    # Speedup
    #
    # S(P) = T(1) / T(P)
    # --------------------------------------------------------

    speedups = [
        t1 / t
        for t in times
    ]

    # --------------------------------------------------------
    # Efficiency
    #
    # E(P) = S(P) / P
    # --------------------------------------------------------

    efficiencies = [
        speedups[i] / thread_counts[i]
        for i in range(len(thread_counts))
    ]

    # --------------------------------------------------------
    # Print results
    # --------------------------------------------------------

    print()

    print(
        f"{'P':>5} "
        f"{'Time(s)':>15} "
        f"{'Speedup':>15} "
        f"{'Efficiency':>15}"
    )

    print("-" * 55)

    for i, p in enumerate(thread_counts):

        print(
            f"{p:5d} "
            f"{times[i]:15.6f} "
            f"{speedups[i]:15.4f} "
            f"{efficiencies[i] * 100:14.2f}%"
        )

    # ========================================================
    # GRAPH
    # ========================================================

    ideal_speedup = thread_counts

    plt.figure(figsize=(9, 6))

    plt.plot(
        thread_counts,
        speedups,
        marker="o",
        label="Observed Speedup"
    )

    plt.plot(
        thread_counts,
        ideal_speedup,
        marker="o",
        linestyle="--",
        label="Ideal Linear Speedup"
    )

    plt.xlabel("Number of Threads (P)")

    plt.ylabel("Speedup")

    plt.title(
        "Parallel Reduction: Observed vs Ideal Speedup"
    )

    plt.xticks(thread_counts)

    plt.grid(True)

    plt.legend()

    plt.tight_layout()

    plt.savefig(
        "task2_4_speedup.png",
        dpi=300
    )

    plt.show()

    return speedups, efficiencies


# ============================================================
# SAVE CSV RESULTS
# ============================================================

def save_results(
    race_results,
    critical_results,
    reduction_results,
    speedups,
    efficiencies
):

    # --------------------------------------------------------
    # Task 2.1
    # --------------------------------------------------------

    with open(
        "task2_1_naive_race.csv",
        "w",
        encoding="utf-8"
    ) as file:

        file.write(
            "Threads,Pi,Absolute_Error\n"
        )

        for p, pi_value, error in race_results:

            file.write(
                f"{p},{pi_value:.12f},{error:.12e}\n"
            )

    # --------------------------------------------------------
    # Task 2.2
    # --------------------------------------------------------

    with open(
        "task2_2_critical.csv",
        "w",
        encoding="utf-8"
    ) as file:

        file.write(
            "Threads,Pi,Time_seconds,Absolute_Error,Overhead_percent\n"
        )

        for (
            p,
            pi_value,
            execution_time,
            error,
            overhead
        ) in critical_results:

            file.write(
                f"{p},"
                f"{pi_value:.12f},"
                f"{execution_time:.6f},"
                f"{error:.12e},"
                f"{overhead:.2f}\n"
            )

    # --------------------------------------------------------
    # Task 2.3 + 2.4
    # --------------------------------------------------------

    with open(
        "task2_3_4_reduction.csv",
        "w",
        encoding="utf-8"
    ) as file:

        file.write(
            "Threads,Average_Time,Std,Pi,Error,Speedup,Efficiency\n"
        )

        for i, p in enumerate(
            reduction_results.keys()
        ):

            data = reduction_results[p]

            file.write(
                f"{p},"
                f"{data['average']:.6f},"
                f"{data['std']:.6f},"
                f"{data['pi']:.12f},"
                f"{data['error']:.12e},"
                f"{speedups[i]:.4f},"
                f"{efficiencies[i] * 100:.2f}\n"
            )


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":

    print()
    print("=" * 70)
    print("LAB 2 - NUMERICAL INTEGRATION AND PARALLEL REDUCTION")
    print("=" * 70)

    # --------------------------------------------------------
    # JIT WARM-UP
    # --------------------------------------------------------

    print("\nCompiling Numba functions...")

    calc_pi_serial(1000)

    calc_pi_naive(1000)

    calc_pi_critical(1000)

    calc_pi_reduction(1000)

    print("Compilation complete.")

    # --------------------------------------------------------
    # Task 2.1
    # --------------------------------------------------------

    race_results = task_2_1()

    # --------------------------------------------------------
    # Task 2.2
    # --------------------------------------------------------

    serial_time, critical_results = task_2_2()

    # --------------------------------------------------------
    # Task 2.3
    # --------------------------------------------------------

    reduction_results = task_2_3()

    # --------------------------------------------------------
    # Task 2.4
    # --------------------------------------------------------

    speedups, efficiencies = task_2_4(
        reduction_results
    )

    # --------------------------------------------------------
    # Save everything
    # --------------------------------------------------------

    save_results(
        race_results,
        critical_results,
        reduction_results,
        speedups,
        efficiencies
    )

    print()
    print("=" * 70)
    print("ALL TASKS COMPLETE")
    print("=" * 70)

    print("\nGenerated files:")

    print("  task2_1_naive_race.csv")

    print("  task2_2_critical.csv")

    print("  task2_3_4_reduction.csv")

    print("  task2_4_speedup.png")