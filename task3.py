import threading
import time

NUM_THREADS = 10
INCREMENTS = 1_000_000
EXPECTED = NUM_THREADS * INCREMENTS


# --------------------------------------------------
# UNSYNCHRONIZED VERSION
# --------------------------------------------------

def increment_without_lock():
    global counter

    for _ in range(INCREMENTS):
        # Force a scheduling opportunity between
        # reading and writing the shared value.
        value = counter
        time.sleep(0)
        counter = value + 1


# --------------------------------------------------
# LOCKED VERSION
# --------------------------------------------------

def increment_with_lock(lock):
    global counter

    for _ in range(INCREMENTS):
        with lock:
            counter += 1


if __name__ == "__main__":

    print("TASK 3: Shared Counter Race Condition")
    print(f"Threads: {NUM_THREADS}")
    print(f"Increments per thread: {INCREMENTS}")
    print(f"Expected total: {EXPECTED:,}")
    print()

    # ==============================================
    # UNSYNCHRONIZED: 10 RUNS
    # ==============================================

    print("=== UNSYNCHRONIZED ===")

    for run in range(1, 11):

        counter = 0

        start = time.perf_counter()

        threads = []

        for _ in range(NUM_THREADS):
            t = threading.Thread(
                target=increment_without_lock
            )
            threads.append(t)
            t.start()

        for t in threads:
            t.join()

        end = time.perf_counter()

        elapsed_ms = (end - start) * 1000
        error = EXPECTED - counter

        print(
            f"Run #{run}: "
            f"Measured = {counter:,}, "
            f"Error = {error:,}, "
            f"Time = {elapsed_ms:.2f} ms"
        )

    # ==============================================
    # LOCKED VERSION
    # ==============================================

    print()
    print("=== LOCKED ===")

    counter = 0
    lock = threading.Lock()

    start = time.perf_counter()

    threads = []

    for _ in range(NUM_THREADS):
        t = threading.Thread(
            target=increment_with_lock,
            args=(lock,)
        )
        threads.append(t)
        t.start()

    for t in threads:
        t.join()

    end = time.perf_counter()

    elapsed_ms = (end - start) * 1000

    print(f"Measured = {counter:,}")
    print(f"Expected = {EXPECTED:,}")
    print(f"Time = {elapsed_ms:.2f} ms")