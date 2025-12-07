package sunshine_dental_care.utils.huybro_utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import sunshine_dental_care.dto.huybro_reports.TopSellingProductDto;
import sunshine_dental_care.dto.huybro_reports.RevenueReportResponseDto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;

public class ExcelExportUtils {

    public static ByteArrayInputStream exportRevenueReportToExcel(
            RevenueReportResponseDto data, LocalDate startDate, LocalDate endDate) {

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Revenue Report");

            // --- STYLES ---
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("#,##0.00"));

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
            throw new RuntimeException("Fail to import data to Excel file: " + e.getMessage());
        }
    }

    private static void createSummaryRow(Sheet sheet, int rowIdx, String label, double value, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        if (style != null) valueCell.setCellStyle(style);
    }
}