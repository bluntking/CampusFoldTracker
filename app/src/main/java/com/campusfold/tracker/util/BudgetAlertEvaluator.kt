package com.campusfold.tracker.util

import android.content.Context
import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.pref.AppPreferences
import java.util.Calendar

object BudgetAlertEvaluator {

    val THRESHOLDS = listOf(50, 70, 90, 100)

    /**
     * Computes the highest threshold (50, 70, 90, 100) reached given spend and budget.
     * Returns 0 if under 50%.
     */
    fun calculateThreshold(spent: Double, budget: Double): Int {
        if (budget <= 0.0 || spent <= 0.0) return 0
        val percentage = (spent / budget) * 100.0
        return THRESHOLDS.lastOrNull { percentage >= it } ?: 0
    }

    /**
     * Evaluates budget alerts for categories and overall monthly budget.
     * Fires high-priority notifications if 50%, 70%, 90%, or 100% threshold is crossed.
     * Avoids duplicate notifications by tracking highest alerted threshold in DataStore.
     */
    suspend fun evaluateBudgetAlerts(
        context: Context,
        db: AppDatabase,
        prefs: AppPreferences,
        targetCategoryId: Long? = null
    ) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) // 0-based
        val yearMonthKey = String.format("%04d-%02d", year, month + 1)

        val (startOfMonth, endOfMonth) = getMonthTimeBounds(calendar)

        // 1. Evaluate Target Category or All Categories
        val categories = if (targetCategoryId != null) {
            val cat = db.categoryDao().getCategoryById(targetCategoryId)
            if (cat != null) listOf(cat) else emptyList()
        } else {
            db.categoryDao().getAllCategoriesList()
        }

        for (cat in categories) {
            val budget = cat.monthlyBudget
            if (budget <= 0.0) continue

            val currentSpend = db.transactionDao().getMonthSpendForCategory(cat.id, startOfMonth, endOfMonth) ?: 0.0
            val crossedThreshold = calculateThreshold(currentSpend, budget)

            if (crossedThreshold > 0) {
                val previousAlerted = prefs.getCategoryAlertThresholdSync(yearMonthKey, cat.id)
                if (crossedThreshold > previousAlerted) {
                    // Update preference to prevent repeated spam for this threshold
                    prefs.setCategoryAlertThreshold(yearMonthKey, cat.id, crossedThreshold)

                    val percentExact = (currentSpend / budget) * 100.0
                    val remaining = (budget - currentSpend).coerceAtLeast(0.0)

                    val title = if (crossedThreshold >= 100) {
                        "🚨 Budget Exhausted: ${cat.name} (100%)"
                    } else {
                        "⚠️ Budget Alert: ${cat.name} reached ${crossedThreshold}%"
                    }

                    val content = "Spent ₹${currentSpend.toInt()} of ₹${budget.toInt()} (${percentExact.toInt()}%). ₹${remaining.toInt()} remaining this month."

                    NotificationHelper.showBudgetAlertNotification(
                        context = context,
                        title = title,
                        content = content,
                        notificationId = (cat.id * 1000 + crossedThreshold).toInt()
                    )
                }
            }
        }

        // 2. Evaluate Overall Monthly Personal Budget
        val overallBudget = prefs.getOverallMonthlyBudgetSync()
        if (overallBudget > 0.0) {
            val totalPersonalSpend = db.transactionDao().getMonthTotalMyExpense(startOfMonth, endOfMonth) ?: 0.0
            val crossedOverall = calculateThreshold(totalPersonalSpend, overallBudget)

            if (crossedOverall > 0) {
                val previousOverall = prefs.getOverallAlertThresholdSync(yearMonthKey)
                if (crossedOverall > previousOverall) {
                    prefs.setOverallAlertThreshold(yearMonthKey, crossedOverall)

                    val overallPercent = (totalPersonalSpend / overallBudget) * 100.0
                    val overallRemaining = (overallBudget - totalPersonalSpend).coerceAtLeast(0.0)

                    val title = if (crossedOverall >= 100) {
                        "🚨 Overall Monthly Budget Reached: 100%"
                    } else {
                        "⚠️ Overall Spend Alert: ${crossedOverall}% of Monthly Budget"
                    }

                    val content = "Total true personal spend is ₹${totalPersonalSpend.toInt()} of ₹${overallBudget.toInt()} (${overallPercent.toInt()}%). ₹${overallRemaining.toInt()} remaining."

                    NotificationHelper.showBudgetAlertNotification(
                        context = context,
                        title = title,
                        content = content,
                        notificationId = 999900 + crossedOverall
                    )
                }
            }
        }
    }

    fun getMonthTimeBounds(referenceCal: Calendar = Calendar.getInstance()): Pair<Long, Long> {
        val calStart = (referenceCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calStart.timeInMillis

        val calEnd = (referenceCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val end = calEnd.timeInMillis

        return Pair(start, end)
    }
}
