package ai.rever.bosseditor.highlight.lexers

import ai.rever.bosseditor.highlight.*

/**
 * SQL syntax highlighting lexer.
 * Supports ANSI SQL with common dialect extensions.
 */
class SqlLexer : BaseLexer() {

    override val languageId: String = "sql"
    override val fileExtensions: List<String> = listOf("sql", "ddl", "dml")

    companion object {
        private val KEYWORDS = setOf(
            // DML
            "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "REPLACE",
            "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS", "BETWEEN",
            "LIKE", "ILIKE", "IS", "NULL", "TRUE", "FALSE", "UNKNOWN",
            "AS", "ON", "USING", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "NATURAL",
            "GROUP", "BY", "HAVING", "ORDER", "ASC", "DESC", "NULLS", "FIRST", "LAST",
            "LIMIT", "OFFSET", "FETCH", "NEXT", "ROWS", "ONLY", "PERCENT", "WITH", "TIES",
            "UNION", "INTERSECT", "EXCEPT", "ALL", "DISTINCT",
            "CASE", "WHEN", "THEN", "ELSE", "END",
            "INTO", "VALUES", "SET", "DEFAULT",
            // DDL
            "CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "COMMENT",
            "TABLE", "VIEW", "INDEX", "SEQUENCE", "SCHEMA", "DATABASE",
            "COLUMN", "CONSTRAINT", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
            "UNIQUE", "CHECK", "NOT", "NULL", "AUTO_INCREMENT", "IDENTITY",
            "CASCADE", "RESTRICT", "NO", "ACTION",
            "IF", "EXISTS", "REPLACE", "TEMPORARY", "TEMP",
            // DCL
            "GRANT", "REVOKE", "PRIVILEGES", "TO", "PUBLIC", "ROLE",
            // TCL
            "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "TRANSACTION",
            // Other
            "EXPLAIN", "ANALYZE", "VACUUM", "REINDEX", "CLUSTER",
            "DECLARE", "CURSOR", "OPEN", "CLOSE", "DEALLOCATE",
            "EXECUTE", "PREPARE", "CALL", "RETURN", "RETURNS",
            "FUNCTION", "PROCEDURE", "TRIGGER", "EVENT",
            "OVER", "PARTITION", "WINDOW", "RANGE", "UNBOUNDED", "PRECEDING", "FOLLOWING", "CURRENT", "ROW"
        )

        private val TYPES = setOf(
            // Numeric
            "INT", "INTEGER", "SMALLINT", "BIGINT", "TINYINT", "MEDIUMINT",
            "DECIMAL", "NUMERIC", "FLOAT", "REAL", "DOUBLE", "PRECISION",
            "BIT", "BOOLEAN", "BOOL", "SERIAL", "BIGSERIAL", "SMALLSERIAL",
            // String
            "CHAR", "VARCHAR", "TEXT", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT",
            "NCHAR", "NVARCHAR", "NTEXT", "CHARACTER", "VARYING",
            "BINARY", "VARBINARY", "BLOB", "TINYBLOB", "MEDIUMBLOB", "LONGBLOB",
            "BYTEA", "CLOB",
            // Date/Time
            "DATE", "TIME", "DATETIME", "TIMESTAMP", "TIMESTAMPTZ",
            "YEAR", "INTERVAL", "TIMETZ",
            // Other
            "JSON", "JSONB", "XML", "UUID", "ENUM", "SET",
            "ARRAY", "HSTORE", "INET", "CIDR", "MACADDR",
            "MONEY", "GEOMETRY", "GEOGRAPHY", "POINT", "LINE", "POLYGON"
        )

        private val FUNCTIONS = setOf(
            // Aggregate
            "COUNT", "SUM", "AVG", "MIN", "MAX", "ARRAY_AGG", "STRING_AGG",
            "LISTAGG", "GROUP_CONCAT", "COLLECT",
            // Window
            "ROW_NUMBER", "RANK", "DENSE_RANK", "NTILE", "LEAD", "LAG",
            "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE",
            // String
            "CONCAT", "SUBSTRING", "SUBSTR", "LEFT", "RIGHT", "LENGTH", "LEN",
            "UPPER", "LOWER", "TRIM", "LTRIM", "RTRIM", "REPLACE", "REVERSE",
            "SPLIT_PART", "POSITION", "CHARINDEX", "INSTR", "LOCATE",
            "LPAD", "RPAD", "REPEAT", "SPACE", "FORMAT",
            // Numeric
            "ABS", "CEIL", "CEILING", "FLOOR", "ROUND", "TRUNC", "TRUNCATE",
            "MOD", "POWER", "SQRT", "EXP", "LOG", "LOG10", "LN",
            "SIGN", "RANDOM", "RAND",
            // Date/Time
            "NOW", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
            "DATE_ADD", "DATE_SUB", "DATEDIFF", "DATEADD", "DATEPART",
            "EXTRACT", "YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND",
            "DATE_TRUNC", "TO_DATE", "TO_CHAR", "TO_TIMESTAMP",
            // Null handling
            "COALESCE", "NULLIF", "NVL", "NVL2", "IFNULL", "ISNULL",
            // Conditional
            "IF", "IIF", "DECODE", "GREATEST", "LEAST",
            // Conversion
            "CAST", "CONVERT", "TRY_CAST", "TRY_CONVERT",
            // Other
            "ROW", "OVER", "WITHIN"
        )

        private val OPERATORS = setOf(
            '+', '-', '*', '/', '%', '=', '<', '>', '!', '|', '&', '^', '~'
        )
    }

    override fun tokenizeLine(line: String, lineNumber: Int, startState: LexerState): LineTokens {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var state = startState

        while (pos < line.length) {
            val char = line[pos]

            when (state) {
                LexerState.IN_BLOCK_COMMENT -> {
                    val (endPos, complete) = readBlockComment(line, pos)
                    tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                    pos = endPos
                    if (complete) state = LexerState.NORMAL
                }

                LexerState.NORMAL -> {
                    when {
                        char.isWhitespace() -> pos = skipWhitespace(line, pos)

                        // Single-line comment --
                        matchesAt(line, pos, "--") -> {
                            tokens.add(Token(pos, line.length, TokenType.COMMENT))
                            pos = line.length
                        }

                        // Block comment /**/
                        matchesAt(line, pos, "/*") -> {
                            val (endPos, complete) = readBlockComment(line, pos + 2)
                            tokens.add(Token(pos, endPos, TokenType.COMMENT_BLOCK))
                            pos = endPos
                            if (!complete) state = LexerState.IN_BLOCK_COMMENT
                        }

                        // String literal
                        char == '\'' -> {
                            val endPos = readSqlString(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.STRING))
                            pos = endPos
                        }

                        // Identifier (quoted)
                        char == '"' || char == '`' || char == '[' -> {
                            val endPos = readQuotedIdentifier(line, pos, char)
                            tokens.add(Token(pos, endPos, TokenType.IDENTIFIER))
                            pos = endPos
                        }

                        // Parameter/variable
                        char == '@' || char == ':' || char == '$' -> {
                            val endPos = readParameter(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.VARIABLE))
                            pos = endPos
                        }

                        // Number
                        char.isDigit() || (char == '.' && pos + 1 < line.length && line[pos + 1].isDigit()) -> {
                            val endPos = readNumber(line, pos)
                            tokens.add(Token(pos, endPos, TokenType.NUMBER))
                            pos = endPos.coerceAtLeast(pos + 1)
                        }

                        // Identifier or keyword
                        isIdentifierStart(char) -> {
                            val endPos = readIdentifier(line, pos)
                            val identifier = line.substring(pos, endPos)
                            tokens.add(Token(pos, endPos, classifyIdentifier(identifier)))
                            pos = endPos
                        }

                        // Operators
                        char in OPERATORS || matchesAt(line, pos, "||") || matchesAt(line, pos, "::") -> {
                            val opLen = readOperator(line, pos)
                            tokens.add(Token(pos, pos + opLen, TokenType.OPERATOR))
                            pos += opLen
                        }

                        char == '(' || char == ')' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PARENTHESIS))
                            pos++
                        }

                        char == ',' || char == ';' || char == '.' -> {
                            tokens.add(Token(pos, pos + 1, TokenType.PUNCTUATION))
                            pos++
                        }

                        else -> {
                            tokens.add(Token(pos, pos + 1, TokenType.DEFAULT))
                            pos++
                        }
                    }
                }

                else -> pos++
            }
        }

        return LineTokens(tokens, state)
    }

    override fun classifyIdentifier(identifier: String): TokenType {
        val upper = identifier.uppercase()
        return when {
            upper in KEYWORDS -> TokenType.KEYWORD
            upper in TYPES -> TokenType.TYPE
            upper in FUNCTIONS -> TokenType.FUNCTION_CALL
            else -> TokenType.IDENTIFIER
        }
    }

    private fun readSqlString(line: String, start: Int): Int {
        var pos = start + 1
        while (pos < line.length) {
            if (line[pos] == '\'') {
                // Check for escaped quote ''
                if (pos + 1 < line.length && line[pos + 1] == '\'') {
                    pos += 2
                } else {
                    return pos + 1
                }
            } else {
                pos++
            }
        }
        return line.length
    }

    private fun readQuotedIdentifier(line: String, start: Int, openQuote: Char): Int {
        val closeQuote = when (openQuote) {
            '[' -> ']'
            else -> openQuote
        }
        var pos = start + 1
        while (pos < line.length) {
            if (line[pos] == closeQuote) {
                return pos + 1
            }
            pos++
        }
        return line.length
    }

    private fun readParameter(line: String, start: Int): Int {
        var pos = start + 1
        // Handle numbered params like $1, :1, @1
        while (pos < line.length && line[pos].isDigit()) pos++
        if (pos > start + 1) return pos
        // Handle named params
        while (pos < line.length && (line[pos].isLetterOrDigit() || line[pos] == '_')) pos++
        return pos
    }

    private fun readOperator(line: String, pos: Int): Int {
        val twoChar = listOf("||", "::", "<=", ">=", "<>", "!=", "!<", "!>", ">>", "<<")
        for (op in twoChar) {
            if (matchesAt(line, pos, op)) return 2
        }
        return 1
    }
}
