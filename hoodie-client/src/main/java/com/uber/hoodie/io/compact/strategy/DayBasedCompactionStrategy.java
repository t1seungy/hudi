/*
 *  Copyright (c) 2016 Uber Technologies, Inc. (hoodie-dev-group@uber.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package com.uber.hoodie.io.compact.strategy;

import com.google.common.annotations.VisibleForTesting;
import com.uber.hoodie.avro.model.HoodieCompactionOperation;
import com.uber.hoodie.avro.model.HoodieCompactionPlan;
import com.uber.hoodie.config.HoodieWriteConfig;
import com.uber.hoodie.exception.HoodieException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This strategy orders compactions in reverse order of creation of Hive Partitions. It helps to
 * compact data in latest partitions first and then older capped at the Total_IO allowed.
 */
public class DayBasedCompactionStrategy extends CompactionStrategy {

  // For now, use SimpleDateFormat as default partition format
  private static String datePartitionFormat = "yyyy/MM/dd";
  // Sorts compaction in LastInFirstCompacted order
  private static Comparator<String> comparator = (String leftPartition,
      String rightPartition) -> {
    try {
      Date left = new SimpleDateFormat(datePartitionFormat, Locale.ENGLISH)
          .parse(leftPartition);
      Date right = new SimpleDateFormat(datePartitionFormat, Locale.ENGLISH)
          .parse(rightPartition);
      return left.after(right) ? -1 : right.after(left) ? 1 : 0;
    } catch (ParseException e) {
      throw new HoodieException("Invalid Partition Date Format", e);
    }
  };

  @VisibleForTesting
  public Comparator<String> getComparator() {
    return comparator;
  }

  @Override
  public List<HoodieCompactionOperation> orderAndFilter(HoodieWriteConfig writeConfig,
      List<HoodieCompactionOperation> operations, List<HoodieCompactionPlan> pendingCompactionPlans) {
    // Iterate through the operations and accept operations as long as we are within the configured target partitions
    // limit
    List<HoodieCompactionOperation> filteredList = operations.stream()
        .collect(Collectors.groupingBy(HoodieCompactionOperation::getPartitionPath)).entrySet().stream()
        .sorted(Map.Entry.comparingByKey(comparator)).limit(writeConfig.getTargetPartitionsPerDayBasedCompaction())
        .flatMap(e -> e.getValue().stream())
        .collect(Collectors.toList());
    return filteredList;
  }
}