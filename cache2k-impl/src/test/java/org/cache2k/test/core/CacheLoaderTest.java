package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.cache2k.integration.FunctionalCacheLoader;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.Condition;
import org.cache2k.test.util.ConcurrencyHelper;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.integration.CacheLoader;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.testing.category.FastTests;
import org.cache2k.test.util.IntCacheRule;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.cache2k.test.core.StaticUtil.*;

/**
 * Test the cache loader.
 *
 * @author Jens Wilke
 * @see CacheLoader
 * @see AdvancedCacheLoader
 * @see CacheOperationCompletionListener
 *
 */
@Category(FastTests.class)
public class CacheLoaderTest {

  private static ExecutorService EXECUTOR = Executors.newCachedThreadPool();

  @AfterClass
  public static void tearDown() {
    EXECUTOR.shutdown();
  }

  @Rule
  public IntCacheRule target = new IntCacheRule();

  @Test
  public void testFunctionalLoader() {
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.loader(new FunctionalCacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            return key * 7;
          }
        });
      }
    });
    int v = c.get(1);
    assertEquals(7, v);
  }

  @Test
  public void testSeparateLoaderExecutor() {
    final AtomicInteger _executionCount = new AtomicInteger(0);
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            return key * 2;
          }
        });
        b.loaderExecutor(new Executor() {
          @Override
          public void execute(final Runnable command) {
            _executionCount.incrementAndGet();
            EXECUTOR.execute(command);
          }
        });
      }
    });
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertEquals(0, _executionCount.get());
    CompletionWaiter _waiter = new CompletionWaiter();
    c.loadAll(toIterable(1, 2, 3), _waiter);
    _waiter.awaitCompletion();
    assertEquals("executor is used", 3, _executionCount.get());
    _waiter = new CompletionWaiter();
    c.prefetchAll(toIterable(6, 7, 8), _waiter);
    _waiter.awaitCompletion();
    assertEquals("prefetch uses executor, too", 6, _executionCount.get());
  }

  @Test
  public void testSeparatePrefetchExecutor() {
    final AtomicInteger _executionCount = new AtomicInteger(0);
    final AtomicInteger _prefetchExecutionCount = new AtomicInteger(0);
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            return key * 2;
          }
        });
        b.loaderExecutor(new Executor() {
          @Override
          public void execute(final Runnable command) {
            _executionCount.incrementAndGet();
            EXECUTOR.execute(command);
          }
        });
        b.prefetchExecutor(new Executor() {
          @Override
          public void execute(final Runnable command) {
            _prefetchExecutionCount.incrementAndGet();
            EXECUTOR.execute(command);
          }
        });
      }
    });
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertEquals(0, _executionCount.get());
    CompletionWaiter _waiter = new CompletionWaiter();
    c.loadAll(toIterable(1, 2, 3), _waiter);
    _waiter.awaitCompletion();
    assertEquals("executor is used", 3, _executionCount.get());
    _waiter = new CompletionWaiter();
    c.prefetchAll(toIterable(6, 7, 8), _waiter);
    _waiter.awaitCompletion();
    assertEquals("prefetch does not use loader executor", 3, _executionCount.get());
    assertEquals("extra executor for prefetch used", 3, _prefetchExecutionCount.get());
  }

  @Test
  public void testLoader() {
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b.loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            return key * 2;
          }
        });
      }
    });
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertFalse(c.containsKey(2));
    assertTrue(c.containsKey(5));
  }

  @Test
  public void testLoadNull() {
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .loader(new CacheLoader<Integer, Integer>() {
            @Override
            public Integer load(final Integer key) throws Exception {
              return null;
            }
          })
          .permitNullValues(true);
      }
    });
    assertNull(c.get(5));
    assertTrue(c.containsKey(5));
  }

  @Test
  public void testAdvancedLoader() {
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .loader(new AdvancedCacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key, long currentTime, CacheEntry<Integer,Integer> e) throws Exception {
            return key * 2;
          }
        });
      }
    });
    assertEquals((Integer) 10, c.get(5));
    assertEquals((Integer) 20, c.get(10));
    assertFalse(c.containsKey(2));
    assertTrue(c.containsKey(5));
  }

  @Test
  public void testLoadAll() throws Exception {
    final AtomicInteger _countLoad =  new AtomicInteger();
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            return _countLoad.incrementAndGet();
          }
        });
      }
    });
    c.get(5);
    CompletionWaiter w = new CompletionWaiter();
    c.loadAll(toIterable(5, 6), w);
    w.awaitCompletion();
    assertEquals(2, _countLoad.get());
    assertEquals((Integer) 2, c.get(6));
    c.loadAll(toIterable(5, 6), null);
    c.loadAll(Collections.EMPTY_SET, null);
  }

  @Test
  public void testReloadAll() throws Exception {
    final AtomicInteger _countLoad =  new AtomicInteger();
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            return _countLoad.incrementAndGet();
          }
        });
      }
    });
    c.get(5);
    CompletionWaiter w = new CompletionWaiter();
    c.reloadAll(toIterable(5, 6), w);
    w.awaitCompletion();
    assertEquals(3, _countLoad.get());
    c.reloadAll(toIterable(5, 6), null);
    c.reloadAll(Collections.EMPTY_SET, null);
  }

  @Test
  public void prefetch_noLoader() {
    Cache<Integer,Integer> c = target.cache();
    c.prefetchAll(toIterable(1,2,3), null);
    assertEquals(0, latestInfo(c).getAsyncLoadsStarted());
  }

  @Test
  public void noPrefetchWhenPresent() {
    Cache<Integer,Integer> c = cacheWithLoader();
    c.put(123, 3);
    c.prefetch(123);
    assertTrue(latestInfo(c).getAsyncLoadsStarted() == 0);
  }

  @Test
  public void prefetch() {
    final Cache<Integer,Integer> c = cacheWithLoader();
    c.prefetch(1);
    assertTrue(isLoadStarted(c));
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return c.containsKey(1);
      }
    });
  }

  @Test
  public void prefetchAll() {
    final Cache<Integer,Integer> c = cacheWithLoader();
    c.prefetchAll(toIterable(1,2,3), null);
    assertTrue(isLoadStarted(c));
    ConcurrencyHelper.await(new Condition() {
      @Override
      public boolean check() throws Exception {
        return c.containsKey(1);
      }
    });
  }

  @Test
  public void prefetch_noLoader_listener() {
    Cache<Integer,Integer> c = target.cache();
    CompletionWaiter w = new CompletionWaiter();
    c.prefetchAll(toIterable(1), w);
    w.awaitCompletion();
  }

  @Test
  public void prefetch_listener() {
    final Cache<Integer,Integer> c = cacheWithLoader();
    CompletionWaiter w = new CompletionWaiter();
    c.prefetchAll(toIterable(1), w);
    assertTrue(isLoadStarted(c));
    w.awaitCompletion();
    assertTrue(c.containsKey(1));
  }

  @Test
  public void prefetch_present_listener() {
    final Cache<Integer,Integer> c = cacheWithLoader();
    CompletionWaiter w = new CompletionWaiter();
    c.put(1, 1);
    c.prefetchAll(toIterable(1), w);
    w.awaitCompletion();
    assertTrue(c.containsKey(1));
    assertTrue(latestInfo(c).getAsyncLoadsStarted() == 0);
  }

  @Test
  public void prefetchAll_noLoader_listener() {
    Cache<Integer,Integer> c = target.cache();
    CompletionWaiter w = new CompletionWaiter();
    c.prefetchAll(toIterable(1), w);
    w.awaitCompletion();
  }

  @Test
  public void prefetchAll_listener() {
    final Cache<Integer,Integer> c = cacheWithLoader();
    CompletionWaiter w = new CompletionWaiter();
    c.prefetchAll(toIterable(1), w);
    assertTrue(isLoadStarted(c));
    w.awaitCompletion();
    assertTrue(c.containsKey(1));
  }

  @Test
  public void prefetchAll_present_listener() {
    final Cache<Integer,Integer> c = cacheWithLoader();
    CompletionWaiter w = new CompletionWaiter();
    c.put(1, 1);
    c.prefetchAll(toIterable(1), w);
    w.awaitCompletion();
    assertTrue(c.containsKey(1));
    assertTrue(latestInfo(c).getAsyncLoadsStarted() == 0);
  }

  @Test
  public void prefetchAll_partiallyPresent_listener() {
    final Cache<Integer,Integer> c = cacheWithLoader();
    CompletionWaiter w = new CompletionWaiter();
    c.put(1, 1);
    c.prefetchAll(toIterable(1, 2, 3), w);
    assertTrue(isLoadStarted(c));
    w.awaitCompletion();
    assertTrue(c.containsKey(3));
    assertEquals(
      "expect 2 started loads, since 1 is in the cache (flaky?)",
      2, latestInfo(c).getAsyncLoadsStarted());
  }

  @Test
  public void prefetchWith10Caches() throws Exception {
    for (int i = 0; i < 10; i++){
      Cache<Integer, Integer> c = cacheWithLoader();
      c.prefetch(123);
      assertTrue("Iteration " + i, isLoadStarted(c));
      target.closeCache();
    }
  }

  /**
   * getAsyncLoadsStarted uses the task count from the executor which is not
   * exact. We use is since we only want to know whether the loader will
   * be invoked, testing for the enqueued task is sufficient and faster.
   * Await the execution of the loader as fallback.
   */
  private boolean isLoadStarted(final Cache<Integer, Integer> _c) {
    if (latestInfo(_c).getAsyncLoadsStarted() > 0) {
      return true;
    }
    ConcurrencyHelper.await("Await loader execution", new Condition() {
      @Override
      public boolean check() throws Exception {
        return loaderExecutionCount > 0;
      }
    });
    return true;
  }

  @Test
  public void testNoPrefetchAll() {
    Cache<Integer,Integer> c = cacheWithLoader();
    c.put(1,1);
    c.put(2,2);
    c.put(3,3);
    c.prefetchAll(toIterable(1,2,3), null);
    assertTrue(latestInfo(c).getAsyncLoadsStarted() == 0);
  }

  /**
   * We should always have two loader threads.
   */
  @Test
  public void testTwoLoaderThreadsAndPoolInfo() throws Exception {
    final CountDownLatch _inLoader = new CountDownLatch(2);
    final CountDownLatch _releaseLoader = new CountDownLatch(1);
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            _inLoader.countDown();
            _releaseLoader.await();
            return key * 2;
          }
        });
      }
    });
    c.loadAll(toIterable(1), null);
    c.loadAll(toIterable(2), null);
    _inLoader.await();
    assertEquals(2, latestInfo(c).getAsyncLoadsStarted());
    assertEquals(2, latestInfo(c).getAsyncLoadsInFlight());
    assertEquals(2, latestInfo(c).getLoaderThreadsMaxActive());
    _releaseLoader.countDown();
  }

  /**
   * Start two overlapping loads, expect that one is done in the caller thread,
   * since only one thread is available.
   */
  @Test
  public void testOneLoaderThreadsAndPoolInfo() throws Exception {
    final Thread _callingThread = Thread.currentThread();
    final CountDownLatch _inLoader = new CountDownLatch(1);
    final CountDownLatch _releaseLoader = new CountDownLatch(1);
    final AtomicInteger _asyncCount = new AtomicInteger();
    Cache<Integer,Integer> c = target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .loaderThreadCount(1)
          .loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            if (_callingThread != Thread.currentThread()) {
              _asyncCount.incrementAndGet();
              _inLoader.countDown();
              _releaseLoader.await();
            }
            return key * 2;
          }
        });
      }
    });
    c.loadAll(toIterable(1), null);
    c.loadAll(toIterable(2), null);
    _inLoader.await();
    assertEquals("only one load is separate thread", 1, latestInfo(c).getAsyncLoadsStarted());
    assertEquals("only one load is separate thread", 1, _asyncCount.get());
    assertEquals(1, latestInfo(c).getAsyncLoadsInFlight());
    assertEquals(1, latestInfo(c).getLoaderThreadsMaxActive());
    _releaseLoader.countDown();
  }

  volatile int loaderExecutionCount = 0;

  protected Cache<Integer, Integer> cacheWithLoader() {
    return target.cache(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        b .loader(new CacheLoader<Integer, Integer>() {
          @Override
          public Integer load(final Integer key) throws Exception {
            loaderExecutionCount++;
            return key * 2;
          }
        });
      }
    });
  }

  public static class CompletionWaiter implements CacheOperationCompletionListener {

    CountDownLatch latch = new CountDownLatch(1);
    volatile Throwable exception;

    @Override
    public void onCompleted() {
      latch.countDown();
    }

    @Override
    public void onException(final Throwable _exception) {
      exception = _exception;
      latch.countDown();
    }

    public void awaitCompletion() {
      while (latch.getCount() > 0) {
        try {
          latch.await();
        } catch (InterruptedException ignore) { }
      }
    }

    public Throwable getException() {
      return exception;
    }

  }

}
