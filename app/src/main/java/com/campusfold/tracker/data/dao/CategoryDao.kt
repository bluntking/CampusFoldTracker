package com.campusfold.tracker.data.dao

import androidx.room.*
import com.campusfold.tracker.data.entity.CategoryEntity
import com.campusfold.tracker.data.model.CategoryPeriodSpend
import com.campusfold.tracker.data.model.CategorySpending
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategoriesList(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)

    @Query("""
        SELECT 
            c.id AS categoryId,
            c.name AS categoryName,
            c.colorHex AS colorHex,
            c.monthlyBudget AS monthlyBudget,
            COALESCE(SUM(t.myActualShare), 0.0) AS totalSpent
        FROM categories c
        LEFT JOIN transactions t ON c.id = t.categoryId AND t.type = 'EXPENSE'
        GROUP BY c.id
        ORDER BY totalSpent DESC
    """)
    fun getCategorySpending(): Flow<List<CategorySpending>>

    @Query("""
        SELECT 
            c.id AS categoryId,
            c.name AS categoryName,
            c.colorHex AS colorHex,
            c.monthlyBudget AS monthlyBudget,
            COALESCE(SUM(t.myActualShare), 0.0) AS totalSpent
        FROM categories c
        LEFT JOIN transactions t ON c.id = t.categoryId AND t.type = 'EXPENSE' AND t.timestamp >= :startOfMonth AND t.timestamp <= :endOfMonth
        GROUP BY c.id
        ORDER BY totalSpent DESC
    """)
    fun getCategorySpendingForMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<CategorySpending>>

    @Query("""
        SELECT 
            c.id AS categoryId,
            c.name AS categoryName,
            c.colorHex AS colorHex,
            c.monthlyBudget AS monthlyBudget,
            COALESCE(SUM(t.myActualShare), 0.0) AS totalSpent
        FROM categories c
        LEFT JOIN transactions t ON c.id = t.categoryId AND t.type = 'EXPENSE' AND t.timestamp >= :startOfMonth AND t.timestamp <= :endOfMonth
        GROUP BY c.id
        ORDER BY totalSpent DESC
    """)
    suspend fun getCategorySpendingForMonthSync(startOfMonth: Long, endOfMonth: Long): List<CategorySpending>

    @Query("""
        SELECT 
            c.id AS categoryId,
            c.name AS categoryName,
            c.colorHex AS colorHex,
            COALESCE(SUM(t.myActualShare), 0.0) AS totalSpent
        FROM categories c
        LEFT JOIN transactions t ON c.id = t.categoryId AND t.type = 'EXPENSE' AND t.timestamp >= :start AND t.timestamp < :end
        GROUP BY c.id
    """)
    suspend fun getCategorySpendBetweenSync(start: Long, end: Long): List<CategoryPeriodSpend>
}
