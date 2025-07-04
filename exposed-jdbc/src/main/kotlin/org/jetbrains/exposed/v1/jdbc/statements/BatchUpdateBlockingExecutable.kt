package org.jetbrains.exposed.v1.jdbc.statements

import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL statement that batch updates rows of a table.
 */
open class BatchUpdateBlockingExecutable(
    override val statement: BatchUpdateStatement
) : UpdateBlockingExecutable(statement) {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        return if (this@BatchUpdateBlockingExecutable.statement.data.size == 1) executeUpdate() else executeBatch().sum()
    }
}
