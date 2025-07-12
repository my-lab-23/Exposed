package org.jetbrains.exposed.v1.tests.postgresql

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.RepeatableTestRule
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PostgresqlGeometryTests : DatabaseTestsBase() {
    @get:Rule
    val repeatRule = RepeatableTestRule()

    // Tabella per i test geometrici
    private val geometryTable = object : IntIdTable("test_geometry") {
        val name = varchar("name", 50)
        val location = point("location")
        val boundary = circle("boundary")
        val area = polygon("area")
        val path = path("path")
    }

    // Operatore custom per il confronto di punti
    class PointEqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "~=")

    infix fun Expression<String>.pointEq(other: String): Op<Boolean> =
        PointEqOp(this, stringLiteral(other))

    @Test
    fun testGeometricTypesInsertion() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, geometryTable) {
            // Test di inserimento dei tipi geometrici
            val id1 = geometryTable.insertAndGetId {
                it[name] = "Centro Città"
                it[location] = "(0,0)"
                it[boundary] = "<(0,0),5>"
                it[area] = "((0,0),(5,0),(5,5),(0,5))"
                it[path] = "[(0,0),(2,2),(4,0)]"
            }

            val id2 = geometryTable.insertAndGetId {
                it[name] = "Quartiere Est"
                it[location] = "(10,10)"
                it[boundary] = "<(10,10),3>"
                it[area] = "((8,8),(12,8),(12,12),(8,12))"
                it[path] = "[(8,8),(10,10),(12,8)]"
            }

            val id3 = geometryTable.insertAndGetId {
                it[name] = "Parco Ovest"
                it[location] = "(-5,2)"
                it[boundary] = "<(-5,2),7>"
                it[area] = "((-10,-2),(0,-2),(0,6),(-10,6))"
                it[path] = "[(-10,0),(-5,2),(0,0)]"
            }

            // Verifica che gli ID siano stati creati
            assertNotNull(id1)
            assertNotNull(id2)
            assertNotNull(id3)

            // Verifica che tutti i record siano stati inseriti
            val count = geometryTable.selectAll().count()
            assertEquals(3, count)
        }
    }

    @Test
    fun testGeometricTypesRetrieval() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, geometryTable) {
            // Inserisce un record di test
            geometryTable.insert {
                it[name] = "Test Location"
                it[location] = "(1,2)"
                it[boundary] = "<(1,2),10>"
                it[area] = "((0,0),(3,0),(3,3),(0,3))"
                it[path] = "[(0,0),(1,2),(3,0)]"
            }

            // Recupera e verifica i dati
            val result = geometryTable.selectAll().single()
            
            assertEquals("Test Location", result[geometryTable.name])
            assertEquals("(1.0,2.0)", result[geometryTable.location])  // Formato con decimali
            assertEquals("<(1.0,2.0),10.0>", result[geometryTable.boundary])  // Formato con decimali
            assertEquals("((0.0,0.0),(3.0,0.0),(3.0,3.0),(0.0,3.0))", result[geometryTable.area])  // Formato con decimali
            assertEquals("[(0.0,0.0),(1.0,2.0),(3.0,0.0)]", result[geometryTable.path])  // Formato con decimali
        }
    }

    @Test
    fun testGeometricQueriesWithFilters() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, geometryTable) {
            // Inserisce dati di test
            geometryTable.insert {
                it[name] = "Origine"
                it[location] = "(0,0)"
                it[boundary] = "<(0,0),1>"
                it[area] = "((0,0),(1,0),(1,1),(0,1))"
                it[path] = "[(0,0),(1,1)]"
            }

            geometryTable.insert {
                it[name] = "Lontano"
                it[location] = "(100,100)"
                it[boundary] = "<(100,100),1>"
                it[area] = "((99,99),(101,99),(101,101),(99,101))"
                it[path] = "[(99,99),(101,101)]"
            }

            // Test query con filtro custom per punto specifico
            val originResults = geometryTable.selectAll()
                .where { geometryTable.location pointEq "(0,0)" }
                .toList()

            assertEquals(1, originResults.size)
            assertEquals("Origine", originResults[0][geometryTable.name])

            // Test query con filtro negativo
            val nonOriginResults = geometryTable.selectAll()
                .where { NotOp(geometryTable.location pointEq "(0,0)") }
                .toList()

            assertEquals(1, nonOriginResults.size)
            assertEquals("Lontano", nonOriginResults[0][geometryTable.name])
        }
    }

    @Test
    fun testGeometricTypesUpdate() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, geometryTable) {
            // Inserisce un record iniziale
            val id = geometryTable.insertAndGetId {
                it[name] = "Iniziale"
                it[location] = "(0,0)"
                it[boundary] = "<(0,0),1>"
                it[area] = "((0,0),(1,0),(1,1),(0,1))"
                it[path] = "[(0,0),(1,1)]"
            }

            // Aggiorna i valori geometrici
            geometryTable.update({ geometryTable.id eq id }) {
                it[name] = "Aggiornato"
                it[location] = "(5,5)"
                it[boundary] = "<(5,5),2>"
                it[area] = "((4,4),(6,4),(6,6),(4,6))"
                it[path] = "[(4,4),(5,5),(6,6)]"
            }

            // Verifica l'aggiornamento
            val updated = geometryTable.selectAll().where { geometryTable.id eq id }.single()
            
            assertEquals("Aggiornato", updated[geometryTable.name])
            assertEquals("(5.0,5.0)", updated[geometryTable.location])  // Formato con decimali
            assertEquals("<(5.0,5.0),2.0>", updated[geometryTable.boundary])  // Formato con decimali
            assertEquals("((4.0,4.0),(6.0,4.0),(6.0,6.0),(4.0,6.0))", updated[geometryTable.area])  // Formato con decimali
            assertEquals("[(4.0,4.0),(5.0,5.0),(6.0,6.0)]", updated[geometryTable.path])  // Formato con decimali
        }
    }

    @Test
    fun testMultipleGeometricRecords() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, geometryTable) {
            // Inserisce diversi record con geometrie diverse
            val testData = listOf(
                listOf("A", "(0.0,0.0)", "<(0.0,0.0),1.0>", "((0.0,0.0),(1.0,0.0),(1.0,1.0),(0.0,1.0))", "[(0.0,0.0),(1.0,1.0)]"),
                listOf("B", "(1.0,1.0)", "<(1.0,1.0),2.0>", "((0.0,0.0),(1.0,0.0),(1.0,1.0),(0.0,1.0))", "[(0.0,0.0),(1.0,1.0)]"),
                listOf("C", "(2.0,2.0)", "<(2.0,2.0),3.0>", "((0.0,0.0),(1.0,0.0),(1.0,1.0),(0.0,1.0))", "[(0.0,0.0),(1.0,1.0)]"),
                listOf("D", "(3.0,3.0)", "<(3.0,3.0),4.0>", "((0.0,0.0),(1.0,0.0),(1.0,1.0),(0.0,1.0))", "[(0.0,0.0),(1.0,1.0)]")
            )

            testData.forEach { (name, _, circle, area, path) ->
                geometryTable.insert {
                    it[this.name] = name
                    it[location] = when(name) {
                        "A" -> "(0,0)"
                        "B" -> "(1,1)"
                        "C" -> "(2,2)"
                        "D" -> "(3,3)"
                        else -> "(0,0)"
                    }
                    it[boundary] = circle
                    it[this.area] = area
                    it[this.path] = path
                }
            }

            // Verifica che tutti i record siano stati inseriti
            val allRecords = geometryTable.selectAll().orderBy(geometryTable.name).toList()
            assertEquals(4, allRecords.size)

            // Verifica i singoli record
            testData.forEachIndexed { index, (expectedName, expectedPoint, expectedCircle, expectedArea, expectedPath) ->
                val record = allRecords[index]
                assertEquals(expectedName, record[geometryTable.name])
                assertEquals(expectedPoint, record[geometryTable.location])
                assertEquals(expectedCircle, record[geometryTable.boundary])
                assertEquals(expectedArea, record[geometryTable.area])
                assertEquals(expectedPath, record[geometryTable.path])
            }
        }
    }

    @Test
    fun testGeometricTypesWithNullValues() {
        // Tabella che permette valori null per i tipi geometrici
        val nullableGeometryTable = object : IntIdTable("nullable_geometry") {
            val name = varchar("name", 50)
            val location = point("location").nullable()
            val boundary = circle("boundary").nullable()
            val area = polygon("area").nullable()
            val path = path("path").nullable()
        }

        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_POSTGRES, nullableGeometryTable) {
            // Inserisce record con alcuni valori null
            nullableGeometryTable.insert {
                it[name] = "Solo Nome"
                it[location] = null
                it[boundary] = null
                it[area] = null
                it[path] = null
            }

            nullableGeometryTable.insert {
                it[name] = "Con Punto"
                it[location] = "(5,5)"
                it[boundary] = null
                it[area] = null
                it[path] = null
            }

            // Verifica i record inseriti
            val records = nullableGeometryTable.selectAll().orderBy(nullableGeometryTable.name).toList()
            assertEquals(2, records.size)

            // Primo record (tutto null tranne il nome)
            val firstRecord = records[1] // "Solo Nome"
            assertEquals("Solo Nome", firstRecord[nullableGeometryTable.name])
            assertEquals(null, firstRecord[nullableGeometryTable.location])
            assertEquals(null, firstRecord[nullableGeometryTable.boundary])
            assertEquals(null, firstRecord[nullableGeometryTable.area])
            assertEquals(null, firstRecord[nullableGeometryTable.path])

            // Secondo record (con punto)
            val secondRecord = records[0] // "Con Punto"
            assertEquals("Con Punto", secondRecord[nullableGeometryTable.name])
            assertEquals("(5.0,5.0)", secondRecord[nullableGeometryTable.location])  // Formato con decimali
            assertEquals(null, secondRecord[nullableGeometryTable.boundary])
            assertEquals(null, secondRecord[nullableGeometryTable.area])
            assertEquals(null, secondRecord[nullableGeometryTable.path])
        }
    }
}