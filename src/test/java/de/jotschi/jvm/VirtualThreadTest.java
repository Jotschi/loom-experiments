package de.jotschi.jvm;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class VirtualThreadTest {

	@Test
	public void testCreation() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		Thread.ofVirtual().name("test").start(() -> {
			Thread t = Thread.currentThread();
			assertTrue("The current test is not virtual", t.isVirtual());
			System.out.println("Running");
			latch.countDown();
		});

		if (!latch.await(100, TimeUnit.MILLISECONDS)) {
			fail("Timeout of thread reached");
		}

	}
}
