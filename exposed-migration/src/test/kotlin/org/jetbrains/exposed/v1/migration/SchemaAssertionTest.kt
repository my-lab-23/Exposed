package org.jetbrains.exposed.v1.migration

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaAssertionTest {

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
    fun `test assertSchemaIsCorrect with valid schema should not throw exception`() {
        // Configura il database H2 in memoria per il test
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        
        transaction {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)
            
            // Verifica che non venga lanciata eccezione
            assertSchemaIsCorrect(TestTable)
        }
    }

    @Test
    fun `test assertSchemaIsCorrect with invalid schema should throw SchemaValidationException`() {
        // Configura il database H2 in memoria per il test
        Database.connect("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        
        transaction {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)
            
            // Verifica che venga lanciata l'eccezione quando la definizione della tabella non corrisponde
            val exception = assertThrows<SchemaValidationException> {
                assertSchemaIsCorrect(TestTableWithExtraColumn)
            }
            
            // Verifica che l'eccezione contenga informazioni sulle migrazioni richieste
            assertTrue(exception.message!!.contains("Schema validation failed"))
            assertTrue(exception.migrationStatements.isNotEmpty())
        }
    }

    @Test
    fun `test validateSchema with valid schema should return Valid result`() {
        // Configura il database H2 in memoria per il test
        Database.connect("jdbc:h2:mem:test3;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        
        transaction {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)
            
            // Verifica che il risultato sia valido
            val result = validateSchema(TestTable)
            assertTrue(result.isValid())
            assertTrue(result.retrieveMigrationStatements().isEmpty())
        }
    }

    @Test
    fun `test validateSchema with invalid schema should return Invalid result`() {
        // Configura il database H2 in memoria per il test
        Database.connect("jdbc:h2:mem:test4;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        
        transaction {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)
            
            // Verifica che il risultato sia invalido
            val result = validateSchema(TestTableWithExtraColumn)
            assertTrue(!result.isValid())
            assertTrue(result.retrieveMigrationStatements().isNotEmpty())
        }
    }

    @Test
    fun `test validateSchema with multiple tables`() {
        // Configura il database H2 in memoria per il test
        Database.connect("jdbc:h2:mem:test5;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        
        transaction {
            // Crea le tabelle nel database
            SchemaUtils.create(TestTable)
            
            // Verifica con più tabelle (anche se una sola esiste)
            val result = validateSchema(TestTable, TestTableWithExtraColumn)
            assertTrue(!result.isValid())
            assertTrue(result.retrieveMigrationStatements().isNotEmpty())
        }
    }

    @Test
    fun `test assertSchemaIsCorrect with inBatch parameter`() {
        // Configura il database H2 in memoria per il test
        Database.connect("jdbc:h2:mem:test6;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        
        transaction {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)
            
            // Verifica che funzioni anche con inBatch = true
            assertSchemaIsCorrect(TestTable, inBatch = true)
        }
    }

    @Test
    fun `test validateSchema with inBatch parameter`() {
        // Configura il database H2 in memoria per il test
        Database.connect("jdbc:h2:mem:test7;DB_CLOSE_DELAY=-1", "org.h2.Driver", "sa", "")
        
        transaction {
            // Crea la tabella nel database
            SchemaUtils.create(TestTable)
            
            // Verifica che funzioni anche con inBatch = true
            val result = validateSchema(TestTable, inBatch = true)
            assertTrue(result.isValid())
        }
    }
} 