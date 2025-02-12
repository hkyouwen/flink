/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.catalog.hive;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.catalog.CatalogFunction;
import org.apache.flink.table.catalog.CatalogPartition;
import org.apache.flink.table.catalog.CatalogPartitionSpec;
import org.apache.flink.table.catalog.CatalogTable;
import org.apache.flink.table.catalog.CatalogTestBase;
import org.apache.flink.table.catalog.CatalogView;
import org.apache.flink.table.catalog.stats.CatalogColumnStatistics;
import org.apache.flink.table.catalog.stats.CatalogColumnStatisticsDataBase;
import org.apache.flink.table.catalog.stats.CatalogColumnStatisticsDataBinary;
import org.apache.flink.table.catalog.stats.CatalogColumnStatisticsDataBoolean;
import org.apache.flink.table.catalog.stats.CatalogColumnStatisticsDataDate;
import org.apache.flink.table.catalog.stats.CatalogColumnStatisticsDataDouble;
import org.apache.flink.table.catalog.stats.CatalogColumnStatisticsDataLong;
import org.apache.flink.table.catalog.stats.CatalogColumnStatisticsDataString;
import org.apache.flink.table.catalog.stats.Date;
import org.apache.flink.util.StringUtils;

import org.apache.hadoop.hive.metastore.api.Table;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test for HiveCatalog on Hive metadata.
 */
public class HiveCatalogHiveMetadataTest extends CatalogTestBase {

	@BeforeClass
	public static void init() {
		catalog = HiveTestUtils.createHiveCatalog();
		catalog.open();
	}

	// =====================
	// HiveCatalog doesn't support streaming table operation. Ignore this test in CatalogTest.
	// =====================

	public void testCreateTable_Streaming() throws Exception {
	}

	@Test
	// verifies that input/output formats and SerDe are set for Hive tables
	public void testCreateTable_StorageFormatSet() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createTable(), false);

		Table hiveTable = ((HiveCatalog) catalog).getHiveTable(path1);
		String inputFormat = hiveTable.getSd().getInputFormat();
		String outputFormat = hiveTable.getSd().getOutputFormat();
		String serde = hiveTable.getSd().getSerdeInfo().getSerializationLib();
		assertFalse(StringUtils.isNullOrWhitespaceOnly(inputFormat));
		assertFalse(StringUtils.isNullOrWhitespaceOnly(outputFormat));
		assertFalse(StringUtils.isNullOrWhitespaceOnly(serde));
	}

	// ------ table and column stats ------
	@Test
	public void testAlterTableColumnStatistics() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		TableSchema tableSchema = TableSchema.builder()
											.field("first", DataTypes.STRING())
											.field("second", DataTypes.INT())
											.field("third", DataTypes.BOOLEAN())
											.field("fourth", DataTypes.DATE())
											.field("fifth", DataTypes.DOUBLE())
											.field("sixth", DataTypes.BIGINT())
											.field("seventh", DataTypes.VARBINARY(200))
											.build();
		CatalogTable catalogTable = new HiveCatalogTable(tableSchema, getBatchTableProperties(), TEST_COMMENT);
		catalog.createTable(path1, catalogTable, false);
		Map<String, CatalogColumnStatisticsDataBase> columnStatisticsDataBaseMap = new HashMap<>();
		columnStatisticsDataBaseMap.put("first", new CatalogColumnStatisticsDataString(10, 5.2, 3, 100));
		columnStatisticsDataBaseMap.put("second", new CatalogColumnStatisticsDataLong(0, 1000, 3, 0));
		columnStatisticsDataBaseMap.put("third", new CatalogColumnStatisticsDataBoolean(15, 20, 3));
		columnStatisticsDataBaseMap.put("fourth", new CatalogColumnStatisticsDataDate(new Date(71L), new Date(17923L), 1321, 0L));
		columnStatisticsDataBaseMap.put("fifth", new CatalogColumnStatisticsDataDouble(15.02, 20.01, 3, 10));
		columnStatisticsDataBaseMap.put("sixth", new CatalogColumnStatisticsDataLong(0, 20, 3, 2));
		columnStatisticsDataBaseMap.put("seventh", new CatalogColumnStatisticsDataBinary(150, 20, 3));
		CatalogColumnStatistics catalogColumnStatistics = new CatalogColumnStatistics(columnStatisticsDataBaseMap);
		catalog.alterTableColumnStatistics(path1, catalogColumnStatistics, false);

		checkEquals(catalogColumnStatistics, catalog.getTableColumnStatistics(path1));
	}

	@Test
	public void testAlterPartitionColumnStatistics() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogTable catalogTable = createPartitionedTable();
		catalog.createTable(path1, catalogTable, false);
		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		catalog.createPartition(path1, partitionSpec, createPartition(), true);
		Map<String, CatalogColumnStatisticsDataBase> columnStatisticsDataBaseMap = new HashMap<>();
		columnStatisticsDataBaseMap.put("first", new CatalogColumnStatisticsDataString(10, 5.2, 3, 100));
		CatalogColumnStatistics catalogColumnStatistics = new CatalogColumnStatistics(columnStatisticsDataBaseMap);
		catalog.alterPartitionColumnStatistics(path1, partitionSpec, catalogColumnStatistics, false);

		checkEquals(catalogColumnStatistics, catalog.getPartitionColumnStatistics(path1, partitionSpec));
	}

	// ------ utils ------

	@Override
	public CatalogTable createTable() {
		return new HiveCatalogTable(
			createTableSchema(),
			getBatchTableProperties(),
			TEST_COMMENT
		);
	}

	@Override
	public CatalogTable createAnotherTable() {
		return new HiveCatalogTable(
			createAnotherTableSchema(),
			getBatchTableProperties(),
			TEST_COMMENT
		);
	}

	@Override
	public CatalogTable createStreamingTable() {
		throw new UnsupportedOperationException("HiveCatalog doesn't support streaming tables.");
	}

	@Override
	public CatalogTable createPartitionedTable() {
		return new HiveCatalogTable(
			createTableSchema(),
			createPartitionKeys(),
			getBatchTableProperties(),
			TEST_COMMENT);
	}

	@Override
	public CatalogTable createAnotherPartitionedTable() {
		return new HiveCatalogTable(
			createAnotherTableSchema(),
			createPartitionKeys(),
			getBatchTableProperties(),
			TEST_COMMENT);
	}

	@Override
	public CatalogView createView() {
		return new HiveCatalogView(
			String.format("select * from %s", t1),
			String.format("select * from %s.%s", TEST_CATALOG_NAME, path1.getFullName()),
			createTableSchema(),
			new HashMap<>(),
			"This is a hive view");
	}

	@Override
	public CatalogView createAnotherView() {
		return new HiveCatalogView(
			String.format("select * from %s", t2),
			String.format("select * from %s.%s", TEST_CATALOG_NAME, path2.getFullName()),
			createAnotherTableSchema(),
			new HashMap<>(),
			"This is another hive view");
	}

	@Override
	protected CatalogFunction createFunction() {
		return new HiveCatalogFunction("test.class.name");
	}

	@Override
	protected CatalogFunction createAnotherFunction() {
		return new HiveCatalogFunction("test.another.class.name");
	}

	@Override
	public CatalogPartition createPartition() {
		return new HiveCatalogPartition(getBatchTableProperties());
	}

	@Override
	public void checkEquals(CatalogTable t1, CatalogTable t2) {
		assertEquals(t1.getSchema(), t2.getSchema());
		assertEquals(t1.getComment(), t2.getComment());
		assertEquals(t1.getPartitionKeys(), t2.getPartitionKeys());
		assertEquals(t1.isPartitioned(), t2.isPartitioned());

		// Hive tables may have properties created by itself
		// thus properties of Hive table is a super set of those in its corresponding Flink table
		assertTrue(t2.getProperties().entrySet().containsAll(t1.getProperties().entrySet()));
	}

	@Override
	protected void checkEquals(CatalogView v1, CatalogView v2) {
		assertEquals(v1.getSchema(), v1.getSchema());
		assertEquals(v1.getComment(), v2.getComment());
		assertEquals(v1.getOriginalQuery(), v2.getOriginalQuery());
		assertEquals(v1.getExpandedQuery(), v2.getExpandedQuery());

		// Hive views may have properties created by itself
		// thus properties of Hive view is a super set of those in its corresponding Flink view
		assertTrue(v2.getProperties().entrySet().containsAll(v1.getProperties().entrySet()));
	}

	@Override
	protected void checkEquals(CatalogPartition expected, CatalogPartition actual) {
		assertTrue(expected instanceof HiveCatalogPartition && actual instanceof HiveCatalogPartition);
		assertEquals(expected.getClass(), actual.getClass());
		HiveCatalogPartition hivePartition1 = (HiveCatalogPartition) expected;
		HiveCatalogPartition hivePartition2 = (HiveCatalogPartition) actual;
		assertEquals(hivePartition1.getDescription(), hivePartition2.getDescription());
		assertEquals(hivePartition1.getDetailedDescription(), hivePartition2.getDetailedDescription());
		assertTrue(hivePartition2.getProperties().entrySet().containsAll(hivePartition1.getProperties().entrySet()));
	}
}
