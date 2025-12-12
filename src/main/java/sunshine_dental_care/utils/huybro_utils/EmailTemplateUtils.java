package sunshine_dental_care.utils.huybro_utils;

import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoice;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoiceItem;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class EmailTemplateUtils {

    // Standard Inline CSS for Email Clients (Gmail, Outlook)
    private static final String CSS = """
        <style>
            body { font-family: 'Helvetica Neue', Arial, sans-serif; color: #333; line-height: 1.6; }
            .container { max-width: 600px; margin: 0 auto; border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; }
            .header { padding: 24px; text-align: center; color: white; }
            .status-badge { display: inline-block; background: rgba(255,255,255,0.2); padding: 5px 15px; border-radius: 20px; font-weight: bold; margin-top: 10px; }
            .content { padding: 24px; background: #fff; }
            .info-box { background: #f8fafc; padding: 15px; border-radius: 8px; margin-bottom: 20px; font-size: 14px; }
            .table-items { width: 100%; border-collapse: collapse; margin-top: 10px; }
            .table-items th { text-align: left; color: #64748b; font-size: 12px; text-transform: uppercase; border-bottom: 2px solid #e2e8f0; padding: 10px; }
            .table-items td { padding: 12px 10px; border-bottom: 1px solid #f1f5f9; font-size: 14px; }
            .totals { margin-top: 20px; border-top: 2px solid #e2e8f0; padding-top: 15px; text-align: right; }
            .footer { background: #f8fafc; text-align: center; padding: 20px; font-size: 12px; color: #94a3b8; }
        </style>
    """;

    /**
     * Build generic Email for Checkout and Status Updates
     */
    public static String buildInvoiceEmail(ProductInvoice invoice, List<ProductInvoiceItem> items) {
        String status = invoice.getInvoiceStatus();
        String color = getStatusColor(status);
        String title = getEmailTitle(status);
        String message = getStatusMessage(status);

        // 1. Build Item List
        StringBuilder itemsHtml = new StringBuilder();
        for (ProductInvoiceItem item : items) {
            itemsHtml.append(String.format("""
                <tr>
                    <td>
                        <div style="font-weight: 600;">%s</div>
                        <div style="font-size: 12px; color: #94a3b8;">SKU: %s</div>
                    </td>
                    <td style="text-align: center;">%d</td>
                    <td style="text-align: right; font-weight: 500;">%s</td>
                </tr>
            """, item.getProductNameSnapshot(), item.getSkuSnapshot(), item.getQuantity(), formatMoney(item.getLineTotalAmount(), invoice.getCurrency())));
        }

        // 2. Build Final HTML
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>%s</head>
            <body>
                <div class="container">
                    <div class="header" style="background-color: %s;">
                        <h2 style="margin:0;">%s</h2>
                        <div class="status-badge">%s</div>
                        <p style="margin: 5px 0 0; opacity: 0.9;">#%s</p>
                    </div>

                    <div class="content">
                        <p>Hello <strong>%s</strong>,</p>
                        
                        <div style="margin-bottom: 20px;">
                            %s
                        </div>

                        <div class="info-box">
                            <table width="100%%">
                                <tr>
                                    <td style="color:#64748b;">Order Date</td>
                                    <td style="text-align:right;">%s</td>
                                </tr>
                                <tr>
                                    <td style="color:#64748b;">Payment</td>
                                    <td style="text-align:right;">%s (%s)</td>
                                </tr>
                                <tr>
                                    <td style="color:#64748b; vertical-align: top;">Delivery To</td>
                                    <td style="text-align:right;">%s</td>
                                </tr>
                            </table>
                        </div>

                        <table class="table-items">
                            <thead>
                                <tr>
                                    <th>Product</th>
                                    <th style="text-align: center;">Qty</th>
                                    <th style="text-align: right;">Total</th>
                                </tr>
                            </thead>
                            <tbody>
                                %s
                            </tbody>
                        </table>

                        <div class="totals">
                            <p style="margin: 5px 0;">Subtotal: <strong>%s</strong></p>
                            <p style="margin: 5px 0;">Tax: <strong>%s</strong></p>
                            <h2 style="margin: 10px 0; color: %s;">Total: %s</h2>
                        </div>
                    </div>

                    <div class="footer">
                        <p>This is an automated email, please do not reply.</p>
                        <p>&copy; 2025 Sunshine Dental Care</p>
                    </div>
                </div>
            </body>
            </html>
        """,
                CSS,
                color, // Header bg
                title, // Title
                status, // Badge text
                invoice.getInvoiceCode(),
                invoice.getCustomerFullName(),
                message, // Message
                invoice.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")),
                invoice.getPaymentMethod(), invoice.getPaymentStatus(),
                invoice.getShippingAddress(),
                itemsHtml.toString(), // List items
                formatMoney(invoice.getSubTotal(), invoice.getCurrency()),
                formatMoney(invoice.getTaxTotal(), invoice.getCurrency()),
                color, // Total amount color
                formatMoney(invoice.getTotalAmount(), invoice.getCurrency())
        );
    }

    // --- HELPER METHODS ---

    private static String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) return "0";
        if ("VND".equalsIgnoreCase(currency)) {
            return new DecimalFormat("#,###").format(amount) + " đ";
        }
        return "$" + new DecimalFormat("#,###.00").format(amount);
    }

    private static String getStatusColor(String status) {
        return switch (status) {
            case "NEW" -> "#3b82f6";        // Blue
            case "CONFIRMED" -> "#fbbf24";  // Amber
            case "PROCESSING" -> "#a855f7"; // Purple
            case "COMPLETED" -> "#10b981";  // Emerald
            case "CANCELLED" -> "#f87171";  // Red
            default -> "#64748b";
        };
    }

    private static String getEmailTitle(String status) {
        return switch (status) {
            case "NEW" -> "Order Confirmation";
            case "CONFIRMED" -> "Order Confirmed";
            case "PROCESSING" -> "Processing Order";
            case "COMPLETED" -> "Order Completed";
            case "CANCELLED" -> "Order Cancelled";
            default -> "Order Update";
        };
    }

    private static String getStatusMessage(String status) {
        return switch (status) {
            case "NEW" -> "<p>Thank you for shopping at Sunshine Dental! Your order has been received.</p>";
            case "CONFIRMED" -> "<p>We have confirmed your order and are preparing your items.</p>";
            case "PROCESSING" -> "<p>Your order is being packed and handed over to the shipping carrier.</p>";
            case "COMPLETED" -> "<p>Your order has been successfully delivered. We hope you enjoy your products.</p>";
            case "CANCELLED" -> "<p>We are sorry to inform you that your order has been cancelled.</p>";
            default -> "<p>There is a new update regarding your order.</p>";
        };
    }
}