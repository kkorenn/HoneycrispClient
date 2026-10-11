package dev.honeycrisp.modules;

/** Clicks in the trailing 1 s window (now - 1000, now]. Ring buffer: 64 clicks/s is beyond any human. */
public final class ClickCounter {
	public static final long WINDOW_MS = 1000;
	private final long[] times = new long[64];
	private int head, size;

	public void click(long now) {
		times[(head + size) % times.length] = now;
		if (size < times.length) size++;
		else head = (head + 1) % times.length;
	}

	public int count(long now) {
		while (size > 0 && times[head] <= now - WINDOW_MS) {
			head = (head + 1) % times.length;
			size--;
		}
		return size;
	}
}
