package de.jotschi.jvm;

/**
 * Utility class which provides JDK backport and utility methods.
 */
public final class JavaCompatUtil {

	private JavaCompatUtil() {
	}

	/**
	 * Check whether the provided thread is a project loom virtual thread.
	 * 
	 * @param thread
	 * @return true if the provided thread is a virtual one.
	 */
	public static boolean isVirtual(Thread thread) {
		return false;
	}

	public static void startVirtualThread(Runnable runnable) {
		runnable.run();
	}

	public static boolean supportsVirtualThreads() {
		return false;
	}

}