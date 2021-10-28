package de.jotschi.jvm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class JavaCompatUtilTest {

	@Test
	public void testCreation() throws Exception {
		if (System.getProperty("expect-java18") != null) {
			assertTrue("The version should support virtual threads.", JavaCompatUtil.supportsVirtualThreads());
			final CountDownLatch latch = new CountDownLatch(1);
			JavaCompatUtil.startVirtualThread(() -> {
				Thread t = Thread.currentThread();
				assertTrue("The current test is not virtual", JavaCompatUtil.isVirtual(t));
				System.out.println("Running");
				latch.countDown();

			});
			if (!latch.await(100, TimeUnit.MILLISECONDS)) {
				fail("Timeout of thread reached");
			}
		} else {
			assertFalse(JavaCompatUtil.supportsVirtualThreads());
		}

	}
}
