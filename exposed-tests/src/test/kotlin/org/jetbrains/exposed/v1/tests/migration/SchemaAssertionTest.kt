package org.jetbrains.exposed.v1.tests.migration

import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.migration.SchemaValidationException
import org.jetbrains.exposed.v1.migration.assertSchemaIsCorrect
import org.jetbrains.exposed.v1.migration.validateSchema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.junit.Test

import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.shared.assertFalse
import kotlin.test.assertFailsWith

class SchemaAssertionTest : DatabaseTestsBase() {

    // Tabella di test semplice
    object TestTable : Table("test_table") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        override val primaryKey = PrimaryKey(id)
    }

    // Tabella di test con colonna aggiuntiva per simulare differenze
    object TestTableWithExtraColumn : Table("test_table") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 255)
        val extraColumn = varchar("extra_column", 100) // Colonna extra per simulare differenze
        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testAssertSchemaIsCorrectWithValidSchemaShouldNotThrowException() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)

            // Verifica che non venga lanciata eccezione
            assertSchemaIsCorrect(TestTable)
        }
    }

    @Test
    fun testAssertSchemaIsCorrectWithInvalidSchemaShouldThrowSchemaValidationException() {
        withDb {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)

            // Verifica che venga lanciata l'eccezione quando la definizione della tabella non corrisponde
            val exception = assertFailsWith<SchemaValidationException> {
                assertSchemaIsCorrect(TestTableWithExtraColumn)
            }

            // Verifica che l'eccezione contenga informazioni sulle migrazioni richieste
            assertTrue(exception.message!!.contains("Schema validation failed"))
            assertTrue(exception.migrationStatements.isNotEmpty())
        }
    }

    @Test
    fun testValidateSchemaWithValidSchemaShouldReturnValidResult() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)

            // Verifica che il risultato sia valido
            val result = validateSchema(TestTable)
            assertTrue(result.isValid())
            assertTrue(result.retrieveMigrationStatements().isEmpty())
        }
    }

    @Test
    fun testValidateSchemaWithInvalidSchemaShouldReturnInvalidResult() {
        withDb {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)

            // Verifica che il risultato sia invalido
            val result = validateSchema(TestTableWithExtraColumn)
            assertFalse(result.isValid())
            assertTrue(result.retrieveMigrationStatements().isNotEmpty())
        }
    }

    @Test
    fun testValidateSchemaWithMultipleTables() {
        withDb {
            // Crea le tabelle nel database
            SchemaUtils.create(TestTable)

            // Verifica con più tabelle (anche se una sola esiste)
            val result = validateSchema(TestTable, TestTableWithExtraColumn)
            assertFalse(result.isValid())
            assertTrue(result.retrieveMigrationStatements().isNotEmpty())
        }
    }

    @Test
    fun testAssertSchemaIsCorrectWithInBatchParameter() {
        withDb {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)

            // Verifica che funzioni anche con inBatch = true
            assertSchemaIsCorrect(TestTable, inBatch = true)
        }
    }

    @Test
    fun testValidateSchemaWithInBatchParameter() {
        withDb {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)

            // Verifica che funzioni anche con inBatch = true
            val result = validateSchema(TestTable, TestTableWithExtraColumn, inBatch = true)
            assertFalse(result.isValid())
        }
    }
}