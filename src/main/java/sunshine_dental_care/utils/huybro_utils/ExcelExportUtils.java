package sunshine_dental_care.utils.huybro_utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import sunshine_dental_care.dto.huybro_payroll.PayslipViewDto;
import sunshine_dental_care.dto.huybro_reports.TopSellingProductDto;
import sunshine_dental_care.dto.huybro_reports.RevenueReportResponseDto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ExcelExportUtils {

    // =================================================================================
    // 1. REVENUE REPORT EXPORT
    // =================================================================================
    public static ByteArrayInputStream exportRevenueReportToExcel(
            RevenueReportResponseDto data, LocalDate startDate, LocalDate endDate) {

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Revenue Report");

            // --- STYLES ---
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);

            // --- HEADER INFO ---
            int rowIdx = 0;
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.createCell(0).setCellValue("REVENUE REPORT");

            Row dateRow = sheet.createRow(rowIdx++);
            dateRow.createCell(0).setCellValue("From: " + startDate + " To: " + endDate);

            rowIdx++; // Empty row

            // --- SUMMARY SECTION ---
            Row summaryHeader = sheet.createRow(rowIdx++);
            summaryHeader.createCell(0).setCellValue("Metric");
            summaryHeader.createCell(1).setCellValue("Value");
            summaryHeader.getCell(0).setCellStyle(headerStyle);
            summaryHeader.getCell(1).setCellStyle(headerStyle);

            createSummaryRow(sheet, rowIdx++, "Net Revenue (Completed)", data.getNetRevenue().doubleValue(), currencyStyle);
            createSummaryRow(sheet, rowIdx++, "Potential Revenue (Pending)", data.getPotentialRevenue().doubleValue(), currencyStyle);
            createSummaryRow(sheet, rowIdx++, "Lost Revenue (Cancelled)", data.getLostRevenue().doubleValue(), currencyStyle);
            createSummaryRow(sheet, rowIdx++, "Total Orders (Completed)", data.getTotalOrdersCompleted().doubleValue(), null);

            rowIdx++; // Empty row

            // --- TOP PRODUCTS SECTION ---
            Row tableHeader = sheet.createRow(rowIdx++);
            String[] columns = {"Product Name", "SKU", "Quantity Sold", "Total Revenue"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = tableHeader.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            for (TopSellingProductDto prod : data.getTopProducts()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(prod.getProductName());
                row.createCell(1).setCellValue(prod.getSku());
                row.createCell(2).setCellValue(prod.getTotalSoldQty());

                Cell revenueCell = row.createCell(3);
                revenueCell.setCellValue(prod.getTotalRevenue().doubleValue());
                revenueCell.setCellStyle(currencyStyle);
            }

            // Auto size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Fail to export Revenue data to Excel: " + e.getMessage());
        }
    }

    // =================================================================================
    // 2. PAYROLL EXPORT (NEW ADDITION)
    // =================================================================================
    public static ByteArrayInputStream exportPayrollToExcel(List<PayslipViewDto> payslips, Integer month, Integer year) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Payroll " + month + "-" + year);

            // --- STYLES ---
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);

            // --- COLUMNS DEFINITION ---
            String[] columns = {
                    "Payslip ID", "Emp Code", "Full Name", "Email",
                    "Work Summary (Actual/Std)",
                    "Base Salary", "Allowances", "OT Pay", "Bonus",
                    "GROSS SALARY",
                    "Late Penalty", "Insurance", "Tax (TNCN)", "Advance", "Other Deduct",
                    "NET SALARY", "Note", "Created At"
            };

            // --- HEADER ROW ---
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // --- DATA ROWS ---
            int rowIdx = 1;
            for (PayslipViewDto item : payslips) {
                Row row = sheet.createRow(rowIdx++);
                int colIdx = 0;

                // 1. Basic Info
                row.createCell(colIdx++).setCellValue(item.getId());
                row.createCell(colIdx++).setCellValue(item.getUserCode());
                row.createCell(colIdx++).setCellValue(item.getUserFullName());
                row.createCell(colIdx++).setCellValue(item.getUserEmail());

                // 2. Work Summary (Format: "24/26 (Days)" or "20/25 (Shifts)")
                String workStr;
                if (item.getStandardShiftsSnapshot() > 0) {
                    workStr = item.getActualShifts() + "/" + item.getStandardShiftsSnapshot() + " (Shifts)";
                } else {
                    workStr = item.getActualWorkDays() + "/" + item.getStandardWorkDaysSnapshot() + " (Days)";
                }
                row.createCell(colIdx++).setCellValue(workStr);

                // 3. Income Components
                createNumericCell(row, colIdx++, item.getBaseSalarySnapshot(), currencyStyle);
                createNumericCell(row, colIdx++, item.getAllowanceAmount(), currencyStyle);
                createNumericCell(row, colIdx++, item.getOtSalaryAmount(), currencyStyle);
                createNumericCell(row, colIdx++, item.getBonusAmount(), currencyStyle);

                // 4. GROSS
                createNumericCell(row, colIdx++, item.getGrossSalary(), currencyStyle);

                // 5. Deductions
                createNumericCell(row, colIdx++, item.getLatePenaltyAmount(), currencyStyle);
                createNumericCell(row, colIdx++, item.getInsuranceDeduction(), currencyStyle);
                createNumericCell(row, colIdx++, item.getTaxDeduction(), currencyStyle);
                createNumericCell(row, colIdx++, item.getAdvancePayment(), currencyStyle);
                createNumericCell(row, colIdx++, item.getOtherDeductionAmount(), currencyStyle);

                // 6. NET SALARY
                // Highlight NET cell logic (optional, keeping simple for now)
                createNumericCell(row, colIdx++, item.getNetSalary(), currencyStyle);

                // 7. Metadata
                row.createCell(colIdx++).setCellValue(item.getNote() != null ? item.getNote() : "");
                row.createCell(colIdx++).setCellValue(item.getCreatedAt() != null ? item.getCreatedAt().toString() : "");
            }

            // Auto size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Fail to export Payroll data to Excel: " + e.getMessage());
        }
    }

    // =================================================================================
    // HELPERS
    // =================================================================================

    private static void createSummaryRow(Sheet sheet, int rowIdx, String label, double value, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        if (style != null) valueCell.setCellStyle(style);
    }

    private static void createNumericCell(Row row, int colIdx, Object value, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else {
            cell.setCellValue(0);
        }
        if (style != null) cell.setCellStyle(style);
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex()); // Màu nền header
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0")); // Format VND (không cần số thập phân)
        return style;
    }
}