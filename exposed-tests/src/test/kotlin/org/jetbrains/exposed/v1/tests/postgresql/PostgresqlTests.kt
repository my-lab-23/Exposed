package org.jetbrains.exposed.v1.tests.postgresql

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.RepeatableTestRule
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertFailAndRollback
import org.jetbrains.exposed.v1.tests.shared.assertFalse
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.Rule
import org.junit.Test
import java.sql.ResultSet
import org.jetbrains.exposed.v1.core.*
import kotlin.test.assertEquals

class PostgresqlTests : DatabaseTestsBase() {
    @get:Rule
    val repeatRule = RepeatableTestRule()

    object GeometricTable : Table("geometric_table") {
        val p = point("p")
        val l = line("l")
        val s = lseg("s")
        val b = box("b")
        val pa = path("pa")
        val po = polygon("po")
        val c = circle("c")
    }

    @Test
    fun testPostgresqlGeometricTypes() {
        withTables(TestDB.ALL_POSTGRES, GeometricTable) {
            val point = "(1.0,2.0)"
            val line = "{1.0,2.0,3.0}"
            val segment = "((1.0,2.0),(3.0,4.0))"
            val box = "((1.0,2.0),(3.0,4.0))"
            val path = "((1.0,2.0),(3.0,4.0))"
            val polygon = "((1.0,2.0),(3.0,4.0))"
            val circle = "<(1.0,2.0),3.0>"

            val insertedId = GeometricTable.insertAndGetId {
                it[p] = point
                it[l] = line
                it[s] = segment
                it[b] = box
                it[pa] = path
                it[po] = polygon
                it[c] = circle
            }

            val result = GeometricTable.select { GeometricTable.id eq insertedId }.single()

            assertEquals(point, result[GeometricTable.p])
            assertEquals(line, result[GeometricTable.l])
            assertEquals(segment, result[GeometricTable.s])
            assertEquals(box, result[GeometricTable.b])
            assertEquals(path, result[GeometricTable.pa])
            assertEquals(polygon, result[GeometricTable.po])
            assertEquals(circle, result[GeometricTable.c])
        }
    }


    private val table = object : IntIdTable() {
        val name = varchar("name", 50)
    }

    @Test
    fun testForUpdateOptionsSyntax() {
        val id = 1

        fun Query.city() = map { it[table.name] }.single()

        fun select(option: ForUpdateOption): String {
            return table.selectAll().where { table.id eq id }.forUpdate(option).city()
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, table) {
            val name = "name"
            table.insert {
                it[table.id] = id
                it[table.name] = name
            }
            commit()

            val defaultForUpdateRes = table.selectAll().where { table.id eq id }.city()
            val forUpdateRes = select(ForUpdateOption.ForUpdate)
            val forUpdateOfTableRes = select(PostgreSQL.ForUpdate(ofTables = arrayOf(table)))
            val forShareRes = select(PostgreSQL.ForShare)
            val forShareNoWaitOfTableRes = select(PostgreSQL.ForShare(PostgreSQL.MODE.NO_WAIT, table))
            val forKeyShareRes = select(PostgreSQL.ForKeyShare)
            val forKeyShareSkipLockedRes = select(PostgreSQL.ForKeyShare(PostgreSQL.MODE.SKIP_LOCKED))
            val forNoKeyUpdateRes = select(PostgreSQL.ForNoKeyUpdate)
            val notForUpdateRes = table.selectAll().where { table.id eq id }.notForUpdate().city()

            assertEquals(name, defaultForUpdateRes)
            assertEquals(name, forUpdateRes)
            assertEquals(name, forUpdateOfTableRes)
            assertEquals(name, forShareRes)
            assertEquals(name, forShareNoWaitOfTableRes)
            assertEquals(name, forKeyShareRes)
            assertEquals(name, forKeyShareSkipLockedRes)
            assertEquals(name, forNoKeyUpdateRes)
            assertEquals(name, notForUpdateRes)
        }
    }

    @Test
    fun testPrimaryKeyCreatedInPostgresql() {
        val tableName = "tester"
        val tester1 = object : Table(tableName) {
            val age = integer("age")
        }

        val tester2 = object : Table(tableName) {
            val age = integer("age")

            override val primaryKey = PrimaryKey(age)
        }

        val tester3 = object : IntIdTable(tableName) {
            val age = integer("age")
        }

        fun <T : Any> JdbcTransaction.assertPrimaryKey(transform: (ResultSet) -> T): T? {
            return exec(
                """
                SELECT ct.relname as TABLE_NAME, ci.relname AS PK_NAME
                FROM pg_catalog.pg_class ct
                JOIN pg_index i ON (ct.oid = i.indrelid AND indisprimary)
                JOIN pg_catalog.pg_class ci ON (ci.oid = i.indexrelid)
                WHERE ct.relname IN ('$tableName')
                """.trimIndent()
            ) { rs ->
                transform(rs)
            }
        }
        withDb(TestDB.ALL_POSTGRES) {
            val defaultPKName = "tester_pkey"
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(tester1)
            assertPrimaryKey {
                assertFalse(it.next())
            }

            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(tester2)
            assertPrimaryKey {
                assertTrue(it.next())
                assertEquals(defaultPKName, it.getString("PK_NAME"))
            }

            assertFailAndRollback("Multiple primary keys are not allowed") {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(tester3)
            }

            org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(tester1)
        }
    }
}
