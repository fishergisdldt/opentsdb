// This file is part of OpenTSDB.
// Copyright (C) 2018  The OpenTSDB Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package net.opentsdb.storage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.hbase.async.HBaseClient;
import org.hbase.async.KeyValue;
import org.hbase.async.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.stumbleupon.async.Deferred;

import net.opentsdb.core.Const;
import net.opentsdb.core.TSDB;
import net.opentsdb.data.MillisecondTimeStamp;
import net.opentsdb.data.TimeStamp;
import net.opentsdb.query.filter.TagVFilter;
import net.opentsdb.query.pojo.Filter;
import net.opentsdb.stats.MockTrace;
import net.opentsdb.stats.Span;
import net.opentsdb.storage.Tsdb1xScanners.State;
import net.opentsdb.storage.schemas.tsdb1x.Schema;
import net.opentsdb.uid.UniqueIdType;
import net.opentsdb.utils.Config;
import net.opentsdb.utils.UnitTestException;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ HBaseClient.class, TSDB.class, Config.class, Scanner.class, 
  Const.class, Deferred.class })
public class TestTsdb1xScanner extends UTBase {
  private Tsdb1xScanners owner;
  private Tsdb1xQueryResult results;
  private Schema schema; 
  
  @Before
  public void before() throws Exception {
    results = mock(Tsdb1xQueryResult.class);
    owner = mock(Tsdb1xScanners.class);
    schema = spy(new Schema(tsdb, null));
    when(owner.schema()).thenReturn(schema);
  }
  
  @Test
  public void ctorIllegalArguments() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    try {
      new Tsdb1xScanner(null, hbase_scanner, 0);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
    
    try {
      new Tsdb1xScanner(owner, null, 0);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
  }
  
  @Test
  public void scanFilters() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    trace = new MockTrace(true);
    
    scanner.fetchNext(results, trace.newSpan("UT").start());
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_DOUBLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(0, scanner.keepers.size());
    assertEquals(0, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(eq(UniqueIdType.METRIC), 
        eq(METRIC_BYTES), any(Span.class));
    
    assertEquals(17, trace.spans.size());
    assertEquals("net.opentsdb.storage.Tsdb1xScanner$ScannerCB_0", 
        trace.spans.get(16).id);
    assertEquals("OK", trace.spans.get(16).tags.get("status"));
  }
  
  @Test
  public void scanFiltersReverse() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setReversed(true);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    trace = new MockTrace(true);
    
    scanner.fetchNext(results, trace.newSpan("UT").start());
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_DOUBLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(0, scanner.keepers.size());
    assertEquals(0, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(eq(UniqueIdType.METRIC), 
        eq(METRIC_BYTES), any(Span.class));
    
    assertEquals(17, trace.spans.size());
    assertEquals("net.opentsdb.storage.Tsdb1xScanner$ScannerCB_0", 
        trace.spans.get(16).id);
    assertEquals("OK", trace.spans.get(16).tags.get("status"));
  }
  
  @Test
  public void scanFiltersNSUI() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter("web.*")
            .setTagk("host")
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.NSUI_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(1)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_NSUI_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, never()).scannerDone();
    verify(owner, times(1)).exception(any(Throwable.class));
    assertEquals(0, scanner.keepers.size());
    assertEquals(0, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.EXCEPTION, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersNSUISkip() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter("web.*")
            .setTagk("host")
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.NSUI_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    scanner.skip_nsui = true;
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_SINGLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(0, scanner.keepers.size());
    assertEquals(0, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersStorageException() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter("web.*")
            .setTagk("host")
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.MULTI_SERIES_EX, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(1)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, never()).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, never()).scannerDone();
    verify(owner, times(1)).exception(any(Throwable.class));
    assertEquals(0, scanner.keepers.size());
    assertEquals(0, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.EXCEPTION, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, never()).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersMultiScans() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(17)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_DOUBLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(0, scanner.keepers.size());
    assertEquals(0, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersThrownException() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    doAnswer(new Answer<Void>() {
      int count = 0;
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        if (count++ > 2) {
          throw new UnitTestException();
        }
        return null;
      }
    }).when(results).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(4)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(4)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, never()).scannerDone();
    verify(owner, times(1)).exception(any(Throwable.class));
    assertEquals(0, scanner.keepers.size());
    assertEquals(0, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.EXCEPTION, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersFull() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    doAnswer(new Answer<Void>() {
        int count = 0;
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          if (count++ > 2) {
            when(owner.isFull()).thenReturn(true);
          }
          invocation.callRealMethod();
          return null;
        }
      }).when(schema).baseTimestamp(any(byte[].class), any(TimeStamp.class));
      
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(1)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(1, scanner.keepers.size());
    assertEquals(1, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(1, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + 3600), TAGK_BYTES, TAGV_B_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersFullRowBoundary() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        when(owner.isFull()).thenReturn(true);
        return null;
      }
    }).when(results).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
      
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(1)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(1)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(1, scanner.keepers.size());
    assertEquals(1, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.CONTINUE, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersOwnerException() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    doAnswer(new Answer<Void>() {
      int count = 0;
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        if (count++ > 2) {
          when(owner.hasException()).thenReturn(true);
        }
        return null;
      }
    }).when(results).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(4)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(4)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(0, scanner.keepers.size());
    assertEquals(0, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersSequenceEnd() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    when(owner.sequenceEnd()).thenReturn(
        new MillisecondTimeStamp(((long) TS_DOUBLE_SERIES + (long) TS_DOUBLE_SERIES_INTERVAL) * 1000));
      
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(3)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(2)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(1, scanner.keepers.size());
    assertEquals(1, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + 7200), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + 7200), TAGK_BYTES, TAGV_B_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersSequenceEndReverse() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setReversed(true);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    when(owner.sequenceEnd()).thenReturn(
        new MillisecondTimeStamp(((long) TS_DOUBLE_SERIES + 
            ((long) TS_DOUBLE_SERIES_INTERVAL * 14)) * 1000));
      
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(3)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(2)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(1, scanner.keepers.size());
    assertEquals(1, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + 46800), TAGK_BYTES, TAGV_B_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + 46800), TAGK_BYTES, TAGV_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersSequenceEndMidRow() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(4);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    when(owner.sequenceEnd()).thenReturn(
        new MillisecondTimeStamp(((long) TS_DOUBLE_SERIES + ((long) TS_DOUBLE_SERIES_INTERVAL * 2)) * 1000));
      
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(3)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(1, scanner.keepers.size());
    assertEquals(1, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 3)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 3)), TAGK_BYTES, TAGV_B_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanFiltersSequenceEndMidRowReverse() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setReversed(true);
    hbase_scanner.setMaxNumRows(4);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    when(owner.sequenceEnd()).thenReturn(
        new MillisecondTimeStamp(((long) TS_DOUBLE_SERIES + 
            ((long) TS_DOUBLE_SERIES_INTERVAL * 13)) * 1000));
      
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(3)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(1, scanner.keepers.size());
    assertEquals(1, scanner.skips.size());
    assertTrue(scanner.keys_to_ids.isEmpty());
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 12)), TAGK_BYTES, TAGV_B_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 12)), TAGK_BYTES, TAGV_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void scanNoFilters() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    trace = new MockTrace(true);
    
    scanner.fetchNext(results, trace.newSpan("UT").start());
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_SINGLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
    assertEquals(2, trace.spans.size());
    assertEquals("net.opentsdb.storage.Tsdb1xScanner$ScannerCB_0", 
        trace.spans.get(1).id);
    assertEquals("OK", trace.spans.get(1).tags.get("status"));
  }
  
  @Test
  public void scanNoFiltersMultiScans() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(9)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_SINGLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void scanNoFiltersThrownException() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    doAnswer(new Answer<Void>() {
      int count = 0;
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        if (count++ > 2) {
          throw new UnitTestException();
        }
        return null;
      }
    }).when(results).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(4)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, never()).scannerDone();
    verify(owner, times(1)).exception(any(Throwable.class));
    assertEquals(State.EXCEPTION, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void scanNoFiltersFull() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    doAnswer(new Answer<Void>() {
      int count = 0;
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        if (count++ > 3) {
          when(owner.isFull()).thenReturn(true);
        }
        return null;
      }
    }).when(results).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(3)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(5)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(1, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 18000), TAGK_BYTES, TAGV_BYTES));
  }
  
  @Test
  public void scanNoFiltersFullOnRowBoundary() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    doAnswer(new Answer<Void>() {
      int count = 0;
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        if (count++ > 2) {
          when(owner.isFull()).thenReturn(true);
        }
        return null;
      }
    }).when(results).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(4)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void scanNoFiltersOwnerException() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    doAnswer(new Answer<Void>() {
      int count = 0;
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        if (count++ > 2) {
          when(owner.hasException()).thenReturn(true);
        }
        return null;
      }
    }).when(results).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(3)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(4)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void scanNoFiltersSequenceEnd() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    when(owner.sequenceEnd()).thenReturn(
        new MillisecondTimeStamp((TS_SINGLE_SERIES + 3600L) * 1000));
        
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(2)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 7200), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 10800), TAGK_BYTES, TAGV_BYTES));
  }
  
  @Test
  public void scanNoFiltersSequenceEndMidRow() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    when(owner.sequenceEnd()).thenReturn(new MillisecondTimeStamp((TS_SINGLE_SERIES + 7200L) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(3)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(1, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 10800), TAGK_BYTES, TAGV_BYTES));
  }
  
  @Test
  public void fetchNextOwnerException() throws Exception {
    when(owner.hasException()).thenReturn(true);
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, never()).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, never()).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void fetchNextOwnerFull() throws Exception {
    when(owner.isFull()).thenReturn(true);
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, never()).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, never()).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void fetchNextFiltersBuffer() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp(((long) TS_DOUBLE_SERIES + 
          (long) TS_DOUBLE_SERIES_INTERVAL) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(3)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(2)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 2)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 2)), TAGK_BYTES, TAGV_B_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
    
    // next fetch
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((long) (TS_DOUBLE_SERIES + 
          TS_DOUBLE_SERIES_INTERVAL * 4) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(6)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(5)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(2)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 5)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 5)), TAGK_BYTES, TAGV_B_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
    
    // final fetch
    when(owner.sequenceEnd()).thenReturn(null);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(17)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_DOUBLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(3)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void fetchNextFiltersBufferSequenceEndInBuffer() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter(TAGV_B_STRING + ".*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.DOUBLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(6);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp(((long) TS_DOUBLE_SERIES) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(1)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(1)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(4, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 1)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 1)), TAGK_BYTES, TAGV_B_BYTES));
    assertArrayEquals(scanner.buffer().get(2).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 2)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(3).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 2)), TAGK_BYTES, TAGV_B_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
    
    // next fetch
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((long) (TS_DOUBLE_SERIES + 
          TS_DOUBLE_SERIES_INTERVAL) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(1)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(2)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(2)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 2)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_INTERVAL * 2)), TAGK_BYTES, TAGV_B_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
    
    // final fetch
    when(owner.sequenceEnd()).thenReturn(null);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(7)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_DOUBLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(3)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void fetchNextFiltersBufferNSUISkip() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter("web.*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.NSUI_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(6);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    scanner.skip_nsui = true;
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp(((long) TS_NSUI_SERIES) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(1)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(1)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(5, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_NSUI_SERIES + (TS_NSUI_SERIES_INTERVAL * 1)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_NSUI_SERIES + (TS_NSUI_SERIES_INTERVAL * 1)), TAGK_BYTES, NSUI_TAGV));
    assertArrayEquals(scanner.buffer().get(2).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_NSUI_SERIES + (TS_NSUI_SERIES_INTERVAL * 2)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(3).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_NSUI_SERIES + (TS_NSUI_SERIES_INTERVAL * 2)), TAGK_BYTES, NSUI_TAGV));
    verify(schema, times(1)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
    
    // next fetch
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((long) (TS_NSUI_SERIES + 
          (TS_NSUI_SERIES_INTERVAL * 2)) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(1)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(3)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(2)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(1, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_NSUI_SERIES + (TS_NSUI_SERIES_INTERVAL * 3)), TAGK_BYTES, TAGV_BYTES));
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
    
    // final fetch
    when(owner.sequenceEnd()).thenReturn(null);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(7)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_NSUI_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(3)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void fetchNextFiltersBufferNSUI() throws Exception {
    final Filter filter = Filter.newBuilder()
        .addFilter(TagVFilter.newBuilder()
            .setFilter("web.*")
            .setTagk(TAGK_STRING)
            .setType("regexp"))
        .build();
    when(owner.scannerFilter()).thenReturn(filter);
    
    Scanner hbase_scanner = metricStartStopScanner(Series.NSUI_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(6);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp(((long) TS_NSUI_SERIES) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(1)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(1)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(5, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_NSUI_SERIES + (TS_NSUI_SERIES_INTERVAL * 1)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_NSUI_SERIES + (TS_NSUI_SERIES_INTERVAL * 1)), TAGK_BYTES, NSUI_TAGV));
    assertArrayEquals(scanner.buffer().get(2).get(0).key(),
        makeRowKey(METRIC_BYTES, (TS_NSUI_SERIES + (TS_NSUI_SERIES_INTERVAL * 2)), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(3).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_NSUI_SERIES + (TS_NSUI_SERIES_INTERVAL * 2)), TAGK_BYTES, NSUI_TAGV));
    verify(schema, times(1)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
    
    // next fetch
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((long) (TS_NSUI_SERIES + 
          (TS_NSUI_SERIES_INTERVAL * 2)) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(1)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(3)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, times(1)).exception(any(Throwable.class));
    assertEquals(State.EXCEPTION, scanner.state());
    verify(schema, times(2)).getName(UniqueIdType.METRIC, METRIC_BYTES, null);
  }
  
  @Test
  public void fetchNextNoFiltersBuffer() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((TS_SINGLE_SERIES + 7200L) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(3)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(1, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 10800), TAGK_BYTES, TAGV_BYTES));
    
    // next fetch
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((TS_SINGLE_SERIES + 18000L) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(4)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(6)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(2)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 21600), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 25200), TAGK_BYTES, TAGV_BYTES));
    
    // final fetch
    when(owner.sequenceEnd()).thenReturn(null);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(9)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_SINGLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(3)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void fetchNextNoFiltersBufferSequenceEndInBuffer() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((TS_SINGLE_SERIES + 3600L) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(2)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 7200), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 10800), TAGK_BYTES, TAGV_BYTES));
    
    // next fetch
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((TS_SINGLE_SERIES + 7200L) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(3)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(2)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(1, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 10800), TAGK_BYTES, TAGV_BYTES));
    
    // final fetch
    when(owner.sequenceEnd()).thenReturn(null);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(9)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_SINGLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(3)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void fetchNextNoFiltersBufferFullInBuffer() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((TS_SINGLE_SERIES + 3600L) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(2)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 7200), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 10800), TAGK_BYTES, TAGV_BYTES));
    
    // next fetch
    when(owner.sequenceEnd()).thenReturn(null);
    doAnswer(new Answer<Void>() {
      int count = 0;
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        if (count++ == 0) {
          when(owner.isFull()).thenReturn(true);
        }
        return null;
      }
    }).when(results).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(3)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(2)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(1, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 10800), TAGK_BYTES, TAGV_BYTES));
    
    // final fetch
    when(owner.sequenceEnd()).thenReturn(null);
    when(owner.isFull()).thenReturn(false);
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(9)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(TS_SINGLE_SERIES_COUNT)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(3)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.COMPLETE, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void fetchNextNoFiltersBufferException() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    hbase_scanner.setMaxNumRows(2);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    when(owner.sequenceEnd())
      .thenReturn(new MillisecondTimeStamp((TS_SINGLE_SERIES + 3600L) * 1000));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, never()).close();
    verify(results, times(2)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, never()).exception(any(Throwable.class));
    assertEquals(State.CONTINUE, scanner.state());
    assertEquals(2, scanner.buffer().size());
    assertArrayEquals(scanner.buffer().get(0).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 7200), TAGK_BYTES, TAGV_BYTES));
    assertArrayEquals(scanner.buffer().get(1).get(0).key(), 
        makeRowKey(METRIC_BYTES, (TS_SINGLE_SERIES + 10800), TAGK_BYTES, TAGV_BYTES));
    
    // next fetch
    when(owner.sequenceEnd()).thenReturn(null);
    doThrow(new UnitTestException()).when(results)
      .addData(any(TimeStamp.class), any(byte[].class), 
        any(byte.class), any(byte[].class), any(byte[].class));
    
    scanner.fetchNext(results, null);
    
    verify(hbase_scanner, times(2)).nextRows();
    verify(hbase_scanner, times(1)).close();
    verify(results, times(3)).addData(any(TimeStamp.class), 
        any(byte[].class), any(byte.class), any(byte[].class), any(byte[].class));
    verify(owner, times(1)).scannerDone();
    verify(owner, times(1)).exception(any(Throwable.class));
    assertEquals(State.EXCEPTION, scanner.state());
    assertNull(scanner.buffer());
  }
  
  @Test
  public void decodeSingleColumnNumericPut() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 0 },
        new byte[] { 1 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    
    scanner.decode(row, results);
    
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(0).qualifier(), 
        row.get(0).value());
  }
  
  @Test
  public void decodeSingleColumnNumericPutFiltered() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 0 },
        new byte[] { 1 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    scanner.data_type_filter = Sets.newHashSet((byte) 1);
    
    scanner.decode(row, results);
    
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(0).qualifier(), 
        row.get(0).value());
  }
  
  @Test
  public void decodeMultiColumnNumericPut() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 0 },
        new byte[] { 1 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 1 },
        new byte[] { 2 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 2 },
        new byte[] { 3 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    
    scanner.decode(row, results);
    
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(0).qualifier(), 
        row.get(0).value());
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(1).qualifier(), 
        row.get(1).value());
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(2).qualifier(), 
        row.get(2).value());
  }
  
  @Test
  public void decodeMultiColumnNumericPutFiltered() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 0 },
        new byte[] { 1 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 1 },
        new byte[] { 2 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 2 },
        new byte[] { 3 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    scanner.data_type_filter = Sets.newHashSet((byte) 1);
    
    scanner.decode(row, results);
    
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(0).qualifier(), 
        row.get(0).value());
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(1).qualifier(), 
        row.get(1).value());
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(2).qualifier(), 
        row.get(2).value());
  }
  
  @Test
  public void decodeSingleColumnNumericAppend() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 5, 0, 0 },
        new byte[] { 1, 2, 3, 4 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    
    scanner.decode(row, results);
    
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        Schema.APPENDS_PREFIX, 
        row.get(0).qualifier(), 
        row.get(0).value());
  }
  
  @Test
  public void decodeSingleColumnNumericAppendFiltered() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 5, 0, 0 },
        new byte[] { 1, 2, 3, 4 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    scanner.data_type_filter = Sets.newHashSet((byte) 1);
    
    scanner.decode(row, results);
    
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        Schema.APPENDS_PREFIX, 
        row.get(0).qualifier(), 
        row.get(0).value());
  }
  
  @Test
  public void decodeMultiColumnNumericPutsAndAppend() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 1 },
        new byte[] { 1 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 2 },
        new byte[] { 2 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 5, 0, 0 },
        new byte[] { 3 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    
    scanner.decode(row, results);
    
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(0).qualifier(), 
        row.get(0).value());
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(1).qualifier(), 
        row.get(1).value());
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 5, 
        row.get(2).qualifier(), 
        row.get(2).value());
  }
  
  @Test
  public void decodeMultiColumnNumericPutsAndAppendFiltered() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 1 },
        new byte[] { 1 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 2 },
        new byte[] { 2 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 5, 0, 0 },
        new byte[] { 3 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    scanner.data_type_filter = Sets.newHashSet((byte) 1);
    
    scanner.decode(row, results);
    
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(0).qualifier(), 
        row.get(0).value());
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(1).qualifier(), 
        row.get(1).value());
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 5, 
        row.get(2).qualifier(), 
        row.get(2).value());
  }
  
  @Test
  public void decodeMultiTypes() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 1 },
        new byte[] { 1 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 8, 2, 0 },
        new byte[] { 2 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 5, 0, 0 },
        new byte[] { 3 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    
    scanner.decode(row, results);
    
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(0).qualifier(), 
        row.get(0).value());
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 8, 
        row.get(1).qualifier(), 
        row.get(1).value());
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 5, 
        row.get(2).qualifier(), 
        row.get(2).value());
  }
  
  @Test
  public void decodeMultiTypesFiltered() throws Exception {
    Scanner hbase_scanner = metricStartStopScanner(Series.SINGLE_SERIES, METRIC_BYTES);
    Tsdb1xScanner scanner = new Tsdb1xScanner(owner, hbase_scanner, 0);
    
    final byte[] row_key = makeRowKey(METRIC_BYTES, TS_SINGLE_SERIES, TAGK_BYTES, TAGV_BYTES);
    ArrayList<KeyValue> row = Lists.newArrayList();
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 0, 1 },
        new byte[] { 1 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 8, 2, 0 },
        new byte[] { 2 }
        ));
    row.add(new KeyValue(row_key, Tsdb1xHBaseDataStore.DATA_FAMILY,
        new byte[] { 5, 0, 0 },
        new byte[] { 3 }
        ));
    scanner.base_ts = new MillisecondTimeStamp(TS_SINGLE_SERIES * 1000);
    scanner.data_type_filter = Sets.newHashSet((byte) 1);
    
    scanner.decode(row, results);
    
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 0, 
        row.get(0).qualifier(), 
        row.get(0).value());
    verify(results, times(1)).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 8, 
        row.get(1).qualifier(), 
        row.get(1).value());
    verify(results, never()).addData(scanner.base_ts, 
        schema.getTSUID(row_key), 
        (byte) 5, 
        row.get(2).qualifier(), 
        row.get(2).value());
  }
  
  Scanner metricStartStopScanner(final Series series, final byte[] metric) {
    final Scanner scanner = client.newScanner(DATA_TABLE);
    switch (series) {
    case SINGLE_SERIES:
      scanner.setStartKey(makeRowKey(
          metric, 
          TS_SINGLE_SERIES, 
          (byte[][]) null));
      scanner.setStopKey(makeRowKey(METRIC_BYTES, 
          TS_SINGLE_SERIES + (TS_SINGLE_SERIES_COUNT * TS_SINGLE_SERIES_INTERVAL), 
          (byte[][]) null));
      break;
    case DOUBLE_SERIES:
      scanner.setStartKey(makeRowKey(
          metric, 
          TS_DOUBLE_SERIES, 
          (byte[][]) null));
      scanner.setStopKey(makeRowKey(METRIC_BYTES, 
          TS_DOUBLE_SERIES + (TS_DOUBLE_SERIES_COUNT * TS_DOUBLE_SERIES_INTERVAL), 
          (byte[][]) null));
      break;
    case MULTI_SERIES_EX:
      scanner.setStartKey(makeRowKey(
          metric, 
          TS_MULTI_SERIES_EX, 
          (byte[][]) null));
      scanner.setStopKey(makeRowKey(METRIC_BYTES, 
          TS_MULTI_SERIES_EX + (TS_MULTI_SERIES_EX_COUNT * TS_MULTI_SERIES_INTERVAL), 
          (byte[][]) null));
      break;
    case NSUI_SERIES:
      scanner.setStartKey(makeRowKey(
          metric, 
          TS_NSUI_SERIES, 
          (byte[][]) null));
      scanner.setStopKey(makeRowKey(METRIC_BYTES, 
          TS_NSUI_SERIES + (TS_NSUI_SERIES_COUNT * TS_NSUI_SERIES_INTERVAL), 
          (byte[][]) null));
      break;
    default:
      throw new RuntimeException("YO! Implement me: " + series);
    }
    return scanner;
  }
}