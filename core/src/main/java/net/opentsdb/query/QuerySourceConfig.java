//This file is part of OpenTSDB.
//Copyright (C) 2017-2018  The OpenTSDB Authors.
//
//This program is free software: you can redistribute it and/or modify it
//under the terms of the GNU Lesser General Public License as published by
//the Free Software Foundation, either version 2.1 of the License, or (at your
//option) any later version.  This program is distributed in the hope that it
//will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
//General Public License for more details.  You should have received a copy
//of the GNU Lesser General Public License along with this program.  If not,
//see <http://www.gnu.org/licenses/>.
package net.opentsdb.query;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import net.opentsdb.core.Const;
import net.opentsdb.query.execution.graph.ExecutionGraphNode;
import net.opentsdb.query.filter.MetricFilter;
import net.opentsdb.query.filter.QueryFilter;

/**
 * A simple base config class for {@link TimeSeriesDataSource} nodes.
 * 
 * TODO - this is ugly and needs a lot of re-org and work.
 * 
 * @since 3.0
 */
@JsonInclude(Include.NON_NULL)
@JsonDeserialize(builder = QuerySourceConfig.Builder.class)
public class QuerySourceConfig extends BaseQueryNodeConfig {
  /** The original and complete time series query. */
  private TimeSeriesQuery query;
  
  // TODO - time offsets for period over period
  
  /** A list of data types to fetch. If empty, fetch all. */
  private final List<String> types;
  
  /** A non-null metric filter used to determine the metric(s) to fetch. */
  private final MetricFilter metric;
  
  /** An optional filter ID found in the query. */
  private final String filter_id;
  
  /** An optional filter. If filter_id is set, this is ignored. */
  private final QueryFilter filter;
  
  /** Whether or not to fetch only the last value. */
  private final boolean fetch_last;
  
  /** An optional list of nodes to push down to the driver. */
  private final List<ExecutionGraphNode> push_down_nodes;
  
  /**
   * Private ctor for the builder.
   * @param builder The non-null builder.
   */
  protected QuerySourceConfig(final Builder builder) {
    super(builder);
    if (builder.metric == null) {
      throw new IllegalArgumentException("Metric filter cannot be null.");
    }
    query = builder.query;
    types = builder.types;
    metric = builder.metric;
    filter_id = builder.filterId;
    filter = builder.filter;
    fetch_last = builder.fetchLast;
    push_down_nodes = builder.push_down_nodes;
  }
  
  /** @return The non-null query object. */
  public TimeSeriesQuery query() {
    return query;
  }
  
  /** @param query A non-null query to replace the existing query. */
  public void setTimeSeriesQuery(final TimeSeriesQuery query) {
    this.query = query;
  }
  
  /** @return A list of data types to filter on. If null or empty, fetch
   * all. */
  public List<String> getTypes() {
    return types;
  }
  
  /** @return The non-null metric filter. */
  public MetricFilter getMetric() {
    return metric;
  }
  
  /** @return An optional filter ID to fetch. */
  public String getFilterId() {
    return filter_id;
  }
  
  /** @return The local filter if set, null if not. */
  public QueryFilter getFilter() {
    return filter;
  }
  
  /** @return Returns either the filter linked by {@link #getFilterId()} 
   * or the local filter. If no filter is associated with the config,
   * it returns null. */
  public QueryFilter filter() {
    if (Strings.isNullOrEmpty(filter_id)) {
      return filter;
    }
    if (query == null) {
      return null;
    }
    return query.getFilter(filter_id);
  }
  
  /** @return Whether or not to fetch just the last (latest) value. */
  public boolean getFetchLast() {
    return fetch_last;
  }
  
  /** @return An optional list of push down nodes. May be null. */
  public List<ExecutionGraphNode> getPushDownNodes() {
    return push_down_nodes;
  }
  
  @Override
  public boolean pushDown() {
    return false;
  }
  
  @Override
  public boolean joins() {
    return false;
  }
  
  @Override
  public boolean equals(final Object o) {
    // TODO Auto-generated method stub
    return false;
  }
  
  @Override
  public int compareTo(final QueryNodeConfig o) {
    if (!(o instanceof QuerySourceConfig)) {
      return -1;
    }
    
    // TODO - implement
    return ComparisonChain.start()
        .compare(id, ((QuerySourceConfig) o).id, Ordering.natural().nullsFirst())
        
        .result();
  }

  @Override
  public int hashCode() {
    return buildHashCode().asInt();
  }
  
  @Override
  public HashCode buildHashCode() {
    final List<HashCode> hashes = Lists.newArrayListWithCapacity(2);
    hashes.add(Const.HASH_FUNCTION().newHasher().putString(id == null ? "null" : id, 
        Const.UTF8_CHARSET).hash());
    // TODO - implement in full
    return Hashing.combineOrdered(hashes);
  }
  
  /** @return A new builder. */
  public static Builder newBuilder() {
    return new Builder();
  }
  
  /** @return A new builder. */
  public static Builder newBuilder(final QuerySourceConfig config) {
    return (Builder) new Builder()
        .setQuery(config.query)
        .setTypes(config.types != null ? Lists.newArrayList(config.types) : null)
        .setMetric(config.metric)
        .setFilterId(config.filter_id)
        .setQueryFilter(config.filter)
        .setFetchLast(config.fetch_last)
        // TODO - overrides if we keep em.
        .setId(config.id);
    // Skipp push down nodes.
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Builder extends BaseQueryNodeConfig.Builder {
    @JsonProperty
    private TimeSeriesQuery query;
    @JsonProperty
    private List<String> types;
    @JsonProperty
    private MetricFilter metric;
    @JsonProperty
    private String filterId;
    @JsonProperty
    private QueryFilter filter;
    @JsonProperty
    private boolean fetchLast;
    private List<ExecutionGraphNode> push_down_nodes;
    
    /** 
     * @param query The non-null query to execute.
     * @return The builder. 
     */
    public Builder setQuery(final TimeSeriesQuery query) {
      this.query = query;
      return this;
    }
    
    public Builder setTypes(final List<String> types) {
      this.types = types;
      return this;
    }
    
    public Builder addType(final String type) {
      if (types == null) {
        types = Lists.newArrayList();
      }
      types.add(type);
      return this;
    }
    
    public Builder setMetric(final MetricFilter metric) {
      this.metric = metric;
      return this;
    }
    
    public Builder setFilterId(final String filter_id) {
      this.filterId = filter_id;
      return this;
    }
    
    public Builder setQueryFilter(final QueryFilter filter) {
      this.filter = filter;
      return this;
    }
    
    public Builder setFetchLast(final boolean fetch_last) {
      this.fetchLast = fetch_last;
      return this;
    }
    
    public Builder setPushDownNodes(
        final List<ExecutionGraphNode> push_down_nodes) {
      this.push_down_nodes = push_down_nodes;
      return this;
    }
    
    public Builder addPushDownNode(final ExecutionGraphNode node) {
      if (push_down_nodes == null) {
        push_down_nodes = Lists.newArrayList();
      }
      push_down_nodes.add(node);
      return this;
    }
    
    public QuerySourceConfig build() {
      return new QuerySourceConfig(this);
    }
  }

}
