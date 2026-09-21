package com.example.features.reports.domain.model

data class FinancialMetrics(
    val totalRevenue: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val outstandingRent: Double = 0.0,
    val collectionRate: Double = 0.0,
    val totalRooms: Int = 0,
    val occupiedRooms: Int = 0,
    val vacantRooms: Int = 0,
    val occupancyRate: Double = 0.0,
    val vacancyRate: Double = 0.0
)

data class ChartPoint(
    val label: String,
    val value: Double
)

data class RevenueReportData(
    val dailyRevenue: List<ChartPoint> = emptyList(),
    val monthlyRevenue: List<ChartPoint> = emptyList(),
    val yearlyRevenue: List<ChartPoint> = emptyList()
)

data class ExpenseReportData(
    val categoryBreakdown: List<ChartPoint> = emptyList(),
    val monthlyTrend: List<ChartPoint> = emptyList(),
    val highestCategories: List<ChartPoint> = emptyList()
)

data class RentAnalyticsData(
    val paidRent: Double = 0.0,
    val pendingRent: Double = 0.0,
    val partialPayments: Double = 0.0,
    val overduePayments: Double = 0.0,
    val collectionPercentage: Double = 0.0
)

data class OccupancyAnalyticsData(
    val totalRooms: Int = 0,
    val occupiedRooms: Int = 0,
    val vacantRooms: Int = 0,
    val occupancyPercentage: Double = 0.0,
    val roomTypeBreakdown: List<ChartPoint> = emptyList()
)
