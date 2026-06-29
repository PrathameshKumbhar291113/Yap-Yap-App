package com.yapyap.core.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun DatabaseDriverFactory.createDatabase(): YapYapDatabase {
    val driver = createDriver()
    return YapYapDatabase(driver)
}
